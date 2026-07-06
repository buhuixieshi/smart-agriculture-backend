package com.agriculture.controller;

import com.agriculture.entity.Device;
import com.agriculture.entity.TelemetryData;
import com.agriculture.service.AlarmRuleService;
import com.agriculture.service.DeviceService;
import com.agriculture.service.TelemetryService;
import com.agriculture.websocket.RealtimeWebSocketHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/iotda")
public class IotdaReportController {

    private static final String DEFAULT_DEVICE_CODE = "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001";

    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;
    private final TelemetryService telemetryService;
    private final AlarmRuleService alarmRuleService;
    private final RealtimeWebSocketHandler realtimeWebSocketHandler;

    public IotdaReportController(ObjectMapper objectMapper,
                                 DeviceService deviceService,
                                 TelemetryService telemetryService,
                                 AlarmRuleService alarmRuleService,
                                 RealtimeWebSocketHandler realtimeWebSocketHandler) {
        this.objectMapper = objectMapper;
        this.deviceService = deviceService;
        this.telemetryService = telemetryService;
        this.alarmRuleService = alarmRuleService;
        this.realtimeWebSocketHandler = realtimeWebSocketHandler;
    }

    @PostMapping("/report")
    public Map<String, Object> report(@RequestBody JsonNode root) throws Exception {
        String deviceCode = text(root, "deviceId", "deviceCode", "device_id", "device_code");
        if (deviceCode == null || deviceCode.isBlank()) {
            deviceCode = DEFAULT_DEVICE_CODE;
        }
        deviceCode = normalizeDeviceCode(deviceCode);

        Device device = deviceService.getByDeviceCode(deviceCode);
        if (device == null) {
            throw new IllegalArgumentException("Device not found: " + deviceCode);
        }

        JsonNode data = resolveDataNode(root);

        TelemetryData telemetry = new TelemetryData();
        telemetry.setDeviceId(device.getId());
        telemetry.setPlotId(device.getPlotId());
        telemetry.setAirTemperature(decimal(data, "temperature", "airTemperature", "air_temperature", "Temp", "Temperature"));
        telemetry.setAirHumidity(decimal(data, "humidity", "airHumidity", "air_humidity", "Humi", "Humidity"));
        telemetry.setIlluminance(decimal(data, "illuminance", "lux", "lightIntensity", "LightIntensity", "Luminance"));
        telemetry.setSoilMoisture(decimal(data, "soilMoisture", "soil_moisture"));
        telemetry.setLightStatus(text(data, "light_status", "lightStatus", "LightStatus", "light_status_value"));
        telemetry.setPumpStatus(text(data, "motor_status", "pump_status", "motorStatus", "pumpStatus", "MotorStatus"));
        telemetry.setCollectedAt(LocalDateTime.now());

        telemetryService.save(telemetry);
        deviceService.updateHeartbeatByDeviceCode(deviceCode);
        alarmRuleService.checkTelemetry(telemetry);
        pushWebSocket(telemetry);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "hardware telemetry saved");
        result.put("telemetryId", telemetry.getId());
        return result;
    }

    private JsonNode resolveDataNode(JsonNode root) {
        if (root.hasNonNull("data")) {
            return root.get("data");
        }

        JsonNode services = root.get("services");
        if (services != null && services.isArray() && !services.isEmpty()) {
            JsonNode properties = services.get(0).get("properties");
            if (properties != null && !properties.isNull()) {
                return properties;
            }
        }

        return root;
    }

    private String normalizeDeviceCode(String deviceCode) {
        if ("bearpi_001".equalsIgnoreCase(deviceCode)) {
            return DEFAULT_DEVICE_CODE;
        }

        return deviceCode;
    }

    private void pushWebSocket(TelemetryData telemetry) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("id", telemetry.getId());
        data.put("plotId", telemetry.getPlotId());
        data.put("deviceId", telemetry.getDeviceId());
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
    }

    private String text(JsonNode node, String... names) {
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

    private BigDecimal decimal(JsonNode node, String... names) {
        String value = text(node, names);
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
