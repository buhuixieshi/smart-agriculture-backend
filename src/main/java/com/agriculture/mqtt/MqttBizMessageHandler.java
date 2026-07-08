package com.agriculture.mqtt;

import com.agriculture.entity.ControlCommand;
import com.agriculture.entity.Device;
import com.agriculture.entity.TelemetryData;
import com.agriculture.service.AlarmRuleService;
import com.agriculture.service.ControlService;
import com.agriculture.service.DeviceService;
import com.agriculture.service.IrrigationStatsService;
import com.agriculture.service.TelemetryService;
import com.agriculture.service.WaterUsageLimitService;
import com.agriculture.websocket.RealtimeWebSocketHandler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttBizMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttBizMessageHandler.class);
    private static final String REAL_BEARPI_DEVICE_CODE = "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001";

    private final MqttProperties mqttProperties;
    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;
    private final TelemetryService telemetryService;
    private final RealtimeWebSocketHandler realtimeWebSocketHandler;
    private final ControlService controlService;
    private final AlarmRuleService alarmRuleService;
    private final IrrigationStatsService irrigationStatsService;
    private final WaterUsageLimitService waterUsageLimitService;

    public MqttBizMessageHandler(MqttProperties mqttProperties,
                                 ObjectMapper objectMapper,
                                 DeviceService deviceService,
                                 TelemetryService telemetryService,
                                 RealtimeWebSocketHandler realtimeWebSocketHandler,
                                 ControlService controlService,
                                 AlarmRuleService alarmRuleService,
                                 IrrigationStatsService irrigationStatsService,
                                 WaterUsageLimitService waterUsageLimitService) {
        this.mqttProperties = mqttProperties;
        this.objectMapper = objectMapper;
        this.deviceService = deviceService;
        this.telemetryService = telemetryService;
        this.realtimeWebSocketHandler = realtimeWebSocketHandler;
        this.controlService = controlService;
        this.alarmRuleService = alarmRuleService;
        this.irrigationStatsService = irrigationStatsService;
        this.waterUsageLimitService = waterUsageLimitService;
    }

    public void handleMessage(String topic, String payload) {
        log.info("Received MQTT message, topic={}, payload={}", topic, payload);

        if (topic == null) {
            return;
        }

        if (isTelemetryTopic(topic)) {
            handleTelemetry(topic, payload);
        } else if (topic.equals(mqttProperties.getTopics().getHeartbeat())) {
            handleHeartbeat(payload);
        } else if (topic.equals(mqttProperties.getTopics().getControlReply())) {
            handleCommandReply(payload);
        } else {
            log.warn("Ignore unknown mqtt topic: {}", topic);
        }
    }

    private void handleTelemetry(String topic, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            String deviceCode = getText(root, "deviceCode", "device_code", "code");
            String deviceId = getText(root, "deviceId", "device_id");
            if ((deviceCode == null || deviceCode.isBlank()) && (deviceId == null || deviceId.isBlank())) {
                log.warn("Telemetry payload missing deviceId/deviceCode: {}", payload);
                return;
            }

            Device device = resolveTelemetryDevice(deviceCode, deviceId);
            if (device == null) {
                log.warn("Telemetry device not found, deviceCode={}, deviceId={}", deviceCode, deviceId);
                return;
            }

            if (device.getPlotId() == null) {
                log.warn("Telemetry device has no plotId, deviceCode={}", device.getDeviceCode());
                return;
            }

            JsonNode data = root.hasNonNull("data") ? root.get("data") : root;
            boolean soilHumidityPayload = isSoilHumidityPayload(topic, root, data);
            BigDecimal soilMoisture = getDecimal(data,
                    "soilMoisture", "soil_moisture", "soilHumidity", "soil_humidity");
            if (soilMoisture == null && soilHumidityPayload) {
                soilMoisture = getDecimal(data, "humidity", "value");
            }

            TelemetryData telemetry = new TelemetryData();
            telemetry.setDeviceId(device.getId());
            telemetry.setPlotId(device.getPlotId());
            telemetry.setDeviceCode(device.getDeviceCode());
            telemetry.setSoilMoisture(soilMoisture);
            telemetry.setAirTemperature(getDecimal(data, "airTemperature", "temperature", "air_temperature", "Temp"));
            telemetry.setAirHumidity(soilHumidityPayload
                    ? getDecimal(data, "airHumidity", "air_humidity", "Humi")
                    : getDecimal(data, "airHumidity", "humidity", "air_humidity", "Humi"));
            telemetry.setIlluminance(getDecimal(data, "illuminance", "light", "lux", "lightIntensity", "LightIntensity"));
            telemetry.setPumpStatus(getText(data, "pumpStatus", "pump_status", "motor_status", "motorStatus", "MotorStatus"));
            telemetry.setLightStatus(getText(data, "lightStatus", "light_status", "LightStatus", "light_status_value"));
            telemetry.setCollectedAt(parseCollectedAt(root));

            fillMissingTelemetryFields(telemetry);
            telemetryService.save(telemetry);
            deviceService.updateHeartbeatByDeviceCode(device.getDeviceCode());
            alarmRuleService.checkTelemetry(telemetry);
            pushTelemetry(telemetry);

            log.info("Telemetry saved, topic={}, deviceCode={}, telemetryId={}, soilMoisture={}",
                    topic, device.getDeviceCode(), telemetry.getId(), telemetry.getSoilMoisture());
        } catch (Exception e) {
            log.error("Handle telemetry failed, payload={}", payload, e);
        }
    }

    private boolean isTelemetryTopic(String topic) {
        String telemetryTopic = mqttProperties.getTopics().getTelemetry();
        return topic.equals(telemetryTopic) || topic.startsWith(telemetryTopic + "/");
    }

    private void fillMissingTelemetryFields(TelemetryData telemetry) {
        TelemetryData latest = getLatestTelemetry(telemetry);
        if (latest == null) {
            return;
        }

        if (telemetry.getDeviceCode() == null) {
            telemetry.setDeviceCode(latest.getDeviceCode());
        }
        if (telemetry.getSoilMoisture() == null) {
            telemetry.setSoilMoisture(latest.getSoilMoisture());
        }
        if (telemetry.getAirTemperature() == null) {
            telemetry.setAirTemperature(latest.getAirTemperature());
        }
        if (telemetry.getAirHumidity() == null) {
            telemetry.setAirHumidity(latest.getAirHumidity());
        }
        if (telemetry.getIlluminance() == null) {
            telemetry.setIlluminance(latest.getIlluminance());
        }
        if (telemetry.getPumpStatus() == null) {
            telemetry.setPumpStatus(latest.getPumpStatus());
        }
        if (telemetry.getLightStatus() == null) {
            telemetry.setLightStatus(latest.getLightStatus());
        }
    }

    private TelemetryData getLatestTelemetry(TelemetryData telemetry) {
        if (telemetry.getPlotId() == null) {
            return null;
        }

        LambdaQueryWrapper<TelemetryData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TelemetryData::getPlotId, telemetry.getPlotId());

        if (telemetry.getDeviceId() != null) {
            wrapper.eq(TelemetryData::getDeviceId, telemetry.getDeviceId());
        }

        wrapper.orderByDesc(TelemetryData::getCollectedAt)
                .orderByDesc(TelemetryData::getId)
                .last("LIMIT 1");

        return telemetryService.getOne(wrapper, false);
    }

    private Device resolveTelemetryDevice(String deviceCode, String deviceId) {
        Device device = findDevice(deviceCode);
        if (device != null) {
            return device;
        }

        device = findDevice(deviceId);
        if (device != null) {
            return device;
        }

        if (deviceId != null && !deviceId.isBlank()) {
            try {
                device = deviceService.getById(Long.parseLong(deviceId));
                if (device != null) {
                    return device;
                }
            } catch (NumberFormatException ignored) {
                // deviceId can also be a string code from simulators.
            }
        }

        device = deviceService.getByDeviceCode(REAL_BEARPI_DEVICE_CODE);
        if (device != null) {
            log.warn("Telemetry simulator device not found, fallback to BearPi device. deviceCode={}, deviceId={}, fallback={}",
                    deviceCode, deviceId, REAL_BEARPI_DEVICE_CODE);
        }
        return device;
    }

    private Device findDevice(String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            return null;
        }
        return deviceService.getByDeviceCode(deviceCode);
    }

    private boolean isSoilHumidityPayload(String topic, JsonNode root, JsonNode data) {
        String lowerTopic = topic == null ? "" : topic.toLowerCase();
        if (lowerTopic.endsWith("/humidity") || lowerTopic.endsWith("/soil_moisture")) {
            return true;
        }

        if (hasAny(data, "soilMoisture", "soil_moisture", "soilHumidity", "soil_humidity")) {
            return true;
        }

        String sensorType = getText(root, "sensorType", "sensor_type", "type");
        if (sensorType != null) {
            String value = sensorType.toLowerCase();
            if (value.contains("soil") || value.contains("moisture")) {
                return true;
            }
        }

        String deviceId = getText(root, "deviceId", "device_id");
        if (deviceId != null && deviceId.matches("\\d+") && data != null && data.hasNonNull("humidity")) {
            return true;
        }

        return data != null
                && data.hasNonNull("humidity")
                && !hasAny(data, "airHumidity", "air_humidity", "temperature", "airTemperature", "air_temperature");
    }

    private void handleHeartbeat(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            String deviceCode = getText(root, "deviceId", "deviceCode");
            if (deviceCode == null || deviceCode.isBlank()) {
                log.warn("Heartbeat payload missing deviceId/deviceCode: {}", payload);
                return;
            }

            boolean updated = deviceService.updateHeartbeatByDeviceCode(deviceCode);
            log.info("Heartbeat updated, deviceCode={}, updated={}", deviceCode, updated);
        } catch (Exception e) {
            log.error("Handle heartbeat failed, payload={}", payload, e);
        }
    }

    private void handleCommandReply(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            String commandNo = getText(root, "commandId", "commandNo", "command_no");
            String status = getText(root, "status", "result");
            String message = getText(root, "message", "msg", "errorMessage", "error_message");

            if (commandNo == null || commandNo.isBlank()) {
                log.warn("Command reply missing commandId/commandNo: {}", payload);
                return;
            }

            ControlCommand command = controlService.getOne(
                    new LambdaQueryWrapper<ControlCommand>()
                            .eq(ControlCommand::getCommandNo, commandNo)
                            .last("LIMIT 1")
            );

            if (command == null) {
                log.warn("Command not found, commandNo={}", commandNo);
                return;
            }

            String normalizedStatus = normalizeReplyStatus(status);
            command.setStatus(normalizedStatus);
            command.setAckAt(LocalDateTime.now());
            command.setErrorMessage(message);

            controlService.updateById(command);
            if ("SUCCESS".equals(normalizedStatus)) {
                handleSuccessfulIrrigation(command);
            }
            log.info("Command reply handled, commandNo={}, status={}", commandNo, command.getStatus());
        } catch (Exception e) {
            log.error("Handle command reply failed, payload={}", payload, e);
        }
    }

    private void handleSuccessfulIrrigation(ControlCommand command) {
        if (!"PUMP_ON".equals(command.getCommandType()) && !"PUMP_OFF".equals(command.getCommandType())) {
            return;
        }

        Device device = deviceService.getByDeviceCode(command.getDeviceCode());
        if (device == null) {
            log.warn("Skip irrigation record, command device not found, commandNo={}, deviceCode={}",
                    command.getCommandNo(), command.getDeviceCode());
            return;
        }

        if ("PUMP_ON".equals(command.getCommandType())) {
            irrigationStatsService.startIrrigation(device, command);
        } else {
            waterUsageLimitService.checkFinishedRecord(device, irrigationStatsService.finishLatestRunning(device, command));
        }
    }

    private String normalizeReplyStatus(String status) {
        if (status == null || status.isBlank()) {
            return "SUCCESS";
        }

        String value = status.trim().toUpperCase();

        if ("OK".equals(value)
                || "SUCCESS".equals(value)
                || "SUCCEED".equals(value)
                || "TRUE".equals(value)) {
            return "SUCCESS";
        }

        if ("FAIL".equals(value)
                || "FAILED".equals(value)
                || "ERROR".equals(value)
                || "FALSE".equals(value)) {
            return "FAILED";
        }

        return value;
    }

    private void pushTelemetry(TelemetryData telemetry) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("id", telemetry.getId());
            data.put("plotId", telemetry.getPlotId());
            data.put("deviceId", telemetry.getDeviceId());
            data.put("deviceCode", telemetry.getDeviceCode());
            data.put("soilMoisture", telemetry.getSoilMoisture());
            data.put("airTemperature", telemetry.getAirTemperature());
            data.put("airHumidity", telemetry.getAirHumidity());
            data.put("illuminance", telemetry.getIlluminance());
            data.put("pumpStatus", telemetry.getPumpStatus());
            data.put("lightStatus", telemetry.getLightStatus());
            data.put("collectedAt", telemetry.getCollectedAt() == null ? null : telemetry.getCollectedAt().toString());

            Map<String, Object> message = new HashMap<>();
            message.put("type", "TELEMETRY");
            message.put("plotId", telemetry.getPlotId());
            message.put("data", data);

            realtimeWebSocketHandler.sendToAll(objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            log.warn("Push telemetry websocket failed, telemetryId={}", telemetry.getId(), e);
        }
    }

    private LocalDateTime parseCollectedAt(JsonNode root) {
        JsonNode timestamp = root.get("timestamp");

        if (timestamp != null && timestamp.isNumber()) {
            return Instant.ofEpochMilli(timestamp.asLong())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        }

        return LocalDateTime.now();
    }

    private String getText(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }

        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }

        return null;
    }

    private BigDecimal getDecimal(JsonNode node, String... names) {
        String text = getText(node, names);

        if (text == null || text.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean hasAny(JsonNode node, String... names) {
        if (node == null) {
            return false;
        }

        for (String name : names) {
            if (node.hasNonNull(name)) {
                return true;
            }
        }

        return false;
    }
}
