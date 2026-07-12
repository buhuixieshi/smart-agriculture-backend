package com.agriculture.service.impl;

import com.agriculture.entity.Alarm;
import com.agriculture.entity.Device;
import com.agriculture.entity.IrrigationStrategy;
import com.agriculture.entity.TelemetryData;
import com.agriculture.service.AlarmRuleService;
import com.agriculture.service.AlarmService;
import com.agriculture.service.IrrigationStrategyService;
import com.agriculture.service.TelemetryService;
import com.agriculture.websocket.RealtimeWebSocketHandler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlarmRuleServiceImpl implements AlarmRuleService {

    private static final Logger log = LoggerFactory.getLogger(AlarmRuleServiceImpl.class);

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String LOW_SOIL_MOISTURE = "LOW_SOIL_MOISTURE";
    private static final String SOIL_MOISTURE_ABNORMAL = "SOIL_MOISTURE_ABNORMAL";
    private static final String AIR_TEMPERATURE_ABNORMAL = "AIR_TEMPERATURE_ABNORMAL";
    private static final String AIR_HUMIDITY_ABNORMAL = "AIR_HUMIDITY_ABNORMAL";
    private static final String ILLUMINANCE_ABNORMAL = "ILLUMINANCE_ABNORMAL";
    private static final String DEVICE_OFFLINE = "DEVICE_OFFLINE";

    private static final BigDecimal SOIL_MOISTURE_MIN = BigDecimal.ZERO;
    private static final BigDecimal SOIL_MOISTURE_MAX = new BigDecimal("100");
    private static final BigDecimal AIR_TEMPERATURE_MIN = new BigDecimal("-10");
    private static final BigDecimal AIR_TEMPERATURE_MAX = new BigDecimal("80");
    private static final BigDecimal AIR_HUMIDITY_MIN = BigDecimal.ZERO;
    private static final BigDecimal AIR_HUMIDITY_MAX = new BigDecimal("100");
    private static final BigDecimal ILLUMINANCE_MIN = BigDecimal.ZERO;
    private static final BigDecimal ILLUMINANCE_MAX = new BigDecimal("1000");

    private static final String MSG_LOW_SOIL_MOISTURE =
            "\u571f\u58e4\u6e7f\u5ea6\u8fde\u7eed\u4f4e\u4e8e\u9608\u503c\u3002";
    private static final String MSG_DEVICE_OFFLINE =
            "\u8bbe\u5907\u5fc3\u8df3\u8d85\u65f6\u3002";
    private static final String MSG_SOIL_MOISTURE_ABNORMAL =
            "\u571f\u58e4\u6e7f\u5ea6\u6570\u636e\u5f02\u5e38\uff0c\u6b63\u5e38\u8303\u56f4\u5e94\u4e3a0-100\u3002";
    private static final String MSG_AIR_TEMPERATURE_ABNORMAL =
            "\u7a7a\u6c14\u6e29\u5ea6\u6570\u636e\u5f02\u5e38\uff0c\u6b63\u5e38\u8303\u56f4\u5e94\u4e3a-10\u523080\u3002";
    private static final String MSG_AIR_HUMIDITY_ABNORMAL =
            "\u7a7a\u6c14\u6e7f\u5ea6\u6570\u636e\u5f02\u5e38\uff0c\u6b63\u5e38\u8303\u56f4\u5e94\u4e3a0-100\u3002";
    private static final String MSG_ILLUMINANCE_ABNORMAL =
            "\u5149\u7167\u5f3a\u5ea6\u6570\u636e\u5f02\u5e38\uff0c\u6b63\u5e38\u8303\u56f4\u5e94\u4e3a0-1000\u3002";

    private final AlarmService alarmService;
    private final IrrigationStrategyService irrigationStrategyService;
    private final TelemetryService telemetryService;
    private final RealtimeWebSocketHandler realtimeWebSocketHandler;
    private final ObjectMapper objectMapper;

    public AlarmRuleServiceImpl(AlarmService alarmService,
                                IrrigationStrategyService irrigationStrategyService,
                                TelemetryService telemetryService,
                                RealtimeWebSocketHandler realtimeWebSocketHandler,
                                ObjectMapper objectMapper) {
        this.alarmService = alarmService;
        this.irrigationStrategyService = irrigationStrategyService;
        this.telemetryService = telemetryService;
        this.realtimeWebSocketHandler = realtimeWebSocketHandler;
        this.objectMapper = objectMapper;
    }

    @Override
    public void checkTelemetry(TelemetryData telemetryData) {
        if (telemetryData == null
                || telemetryData.getPlotId() == null
                || telemetryData.getDeviceId() == null) {
            return;
        }

        if (checkAbnormalTelemetry(telemetryData)) {
            return;
        }

        if (telemetryData.getSoilMoisture() == null) {
            return;
        }

        IrrigationStrategy strategy = irrigationStrategyService.getOne(
                new LambdaQueryWrapper<IrrigationStrategy>()
                        .eq(IrrigationStrategy::getPlotId, telemetryData.getPlotId())
                        .last("LIMIT 1"),
                false
        );

        if (strategy == null) {
            return;
        }

        BigDecimal moistureMin = strategy.getMoistureMin();
        if (moistureMin == null) {
            return;
        }

        if (telemetryData.getSoilMoisture().compareTo(moistureMin) >= 0) {
            recoverLowMoistureAlarms(telemetryData.getPlotId());
            return;
        }

        Alarm activeAlarm = alarmService.getOne(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getPlotId, telemetryData.getPlotId())
                        .eq(Alarm::getAlarmType, LOW_SOIL_MOISTURE)
                        .in(Alarm::getStatus, STATUS_ACTIVE, "ACKED", "ACKNOWLEDGED")
                        .orderByDesc(Alarm::getCreateTime)
                        .last("LIMIT 1"),
                false
        );

        if (activeAlarm != null) {
            activeAlarm.setTriggerValue(telemetryData.getSoilMoisture());
            activeAlarm.setThresholdValue(moistureMin);
            activeAlarm.setSeverity(resolveMoistureSeverity(telemetryData.getSoilMoisture(), moistureMin));
            activeAlarm.setMessage(MSG_LOW_SOIL_MOISTURE);
            activeAlarm.setCreateTime(LocalDateTime.now());
            alarmService.updateById(activeAlarm);
            log.warn("Low soil moisture alarm refreshed, alarmId={}, plotId={}, deviceId={}, triggerValue={}, thresholdValue={}",
                    activeAlarm.getId(), activeAlarm.getPlotId(), activeAlarm.getDeviceId(),
                    activeAlarm.getTriggerValue(), activeAlarm.getThresholdValue());
            return;
        }

        Alarm alarm = new Alarm();
        alarm.setPlotId(telemetryData.getPlotId());
        alarm.setDeviceId(telemetryData.getDeviceId());
        alarm.setAlarmType(LOW_SOIL_MOISTURE);
        alarm.setSeverity(resolveMoistureSeverity(telemetryData.getSoilMoisture(), moistureMin));
        alarm.setTriggerValue(telemetryData.getSoilMoisture());
        alarm.setThresholdValue(moistureMin);
        alarm.setStatus(STATUS_ACTIVE);
        alarm.setMessage(MSG_LOW_SOIL_MOISTURE);
        alarm.setCreateTime(LocalDateTime.now());

        alarmService.save(alarm);
        pushAlarm(alarm);
        log.warn("Low soil moisture alarm created, alarmId={}, plotId={}, deviceId={}, triggerValue={}, thresholdValue={}",
                alarm.getId(), alarm.getPlotId(), alarm.getDeviceId(), alarm.getTriggerValue(), alarm.getThresholdValue());
    }

    private void recoverLowMoistureAlarms(Long plotId) {
        List<Alarm> alarms = alarmService.list(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getPlotId, plotId)
                        .eq(Alarm::getAlarmType, LOW_SOIL_MOISTURE)
                        .in(Alarm::getStatus, STATUS_ACTIVE, "ACKED", "ACKNOWLEDGED")
        );
        for (Alarm alarm : alarms) {
            pushAlarm(alarmService.recover(alarm.getId()));
        }
    }

    @Override
    public void handleDeviceOffline(Device device) {
        if (device == null || device.getId() == null) {
            return;
        }

        Alarm activeAlarm = alarmService.getOne(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getDeviceId, device.getId())
                        .eq(Alarm::getAlarmType, DEVICE_OFFLINE)
                        .eq(Alarm::getStatus, STATUS_ACTIVE)
                        .orderByDesc(Alarm::getCreateTime)
                        .last("LIMIT 1"),
                false
        );

        if (activeAlarm != null) {
            activeAlarm.setPlotId(device.getPlotId());
            activeAlarm.setMessage(MSG_DEVICE_OFFLINE);
            activeAlarm.setCreateTime(LocalDateTime.now());
            alarmService.updateById(activeAlarm);
            pushAlarm(activeAlarm);
            log.warn("Device offline alarm refreshed, alarmId={}, plotId={}, deviceId={}",
                    activeAlarm.getId(), activeAlarm.getPlotId(), activeAlarm.getDeviceId());
            return;
        }

        Alarm alarm = new Alarm();
        alarm.setPlotId(device.getPlotId());
        alarm.setDeviceId(device.getId());
        alarm.setAlarmType(DEVICE_OFFLINE);
        alarm.setSeverity("CRITICAL");
        alarm.setStatus(STATUS_ACTIVE);
        alarm.setMessage(MSG_DEVICE_OFFLINE);
        alarm.setCreateTime(LocalDateTime.now());

        alarmService.save(alarm);
        pushAlarm(alarm);
        log.warn("Device offline alarm created, alarmId={}, plotId={}, deviceId={}",
                alarm.getId(), alarm.getPlotId(), alarm.getDeviceId());
    }

    private boolean checkAbnormalTelemetry(TelemetryData telemetryData) {
        boolean abnormal = false;
        abnormal |= createSensorAbnormalAlarmIfNeeded(
                telemetryData,
                SOIL_MOISTURE_ABNORMAL,
                telemetryData.getSoilMoisture(),
                SOIL_MOISTURE_MIN,
                SOIL_MOISTURE_MAX,
                MSG_SOIL_MOISTURE_ABNORMAL
        );
        abnormal |= createSensorAbnormalAlarmIfNeeded(
                telemetryData,
                AIR_TEMPERATURE_ABNORMAL,
                telemetryData.getAirTemperature(),
                AIR_TEMPERATURE_MIN,
                AIR_TEMPERATURE_MAX,
                MSG_AIR_TEMPERATURE_ABNORMAL
        );
        abnormal |= createSensorAbnormalAlarmIfNeeded(
                telemetryData,
                AIR_HUMIDITY_ABNORMAL,
                telemetryData.getAirHumidity(),
                AIR_HUMIDITY_MIN,
                AIR_HUMIDITY_MAX,
                MSG_AIR_HUMIDITY_ABNORMAL
        );
        abnormal |= createSensorAbnormalAlarmIfNeeded(
                telemetryData,
                ILLUMINANCE_ABNORMAL,
                telemetryData.getIlluminance(),
                ILLUMINANCE_MIN,
                ILLUMINANCE_MAX,
                MSG_ILLUMINANCE_ABNORMAL
        );
        return abnormal;
    }

    private boolean createSensorAbnormalAlarmIfNeeded(TelemetryData telemetryData,
                                                      String alarmType,
                                                      BigDecimal triggerValue,
                                                      BigDecimal minValue,
                                                      BigDecimal maxValue,
                                                      String message) {
        if (!isAbnormal(triggerValue, minValue, maxValue)) {
            return false;
        }

        BigDecimal thresholdValue = triggerValue.compareTo(minValue) < 0 ? minValue : maxValue;
        Alarm activeAlarm = alarmService.getOne(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getPlotId, telemetryData.getPlotId())
                        .eq(Alarm::getDeviceId, telemetryData.getDeviceId())
                        .eq(Alarm::getAlarmType, alarmType)
                        .eq(Alarm::getThresholdValue, thresholdValue)
                        .eq(Alarm::getStatus, STATUS_ACTIVE)
                        .orderByDesc(Alarm::getCreateTime)
                        .last("LIMIT 1"),
                false
        );

        if (activeAlarm != null) {
            activeAlarm.setTriggerValue(triggerValue);
            activeAlarm.setThresholdValue(thresholdValue);
            activeAlarm.setSeverity("CRITICAL");
            activeAlarm.setMessage(message);
            activeAlarm.setCreateTime(LocalDateTime.now());
            alarmService.updateById(activeAlarm);
            pushAlarm(activeAlarm);
            log.warn("Sensor abnormal alarm refreshed, alarmId={}, plotId={}, deviceId={}, alarmType={}, triggerValue={}, thresholdValue={}",
                    activeAlarm.getId(),
                    activeAlarm.getPlotId(),
                    activeAlarm.getDeviceId(),
                    activeAlarm.getAlarmType(),
                    activeAlarm.getTriggerValue(),
                    activeAlarm.getThresholdValue());
            return true;
        }

        Alarm alarm = new Alarm();
        alarm.setPlotId(telemetryData.getPlotId());
        alarm.setDeviceId(telemetryData.getDeviceId());
        alarm.setAlarmType(alarmType);
        alarm.setSeverity("CRITICAL");
        alarm.setTriggerValue(triggerValue);
        alarm.setThresholdValue(thresholdValue);
        alarm.setStatus(STATUS_ACTIVE);
        alarm.setMessage(message);
        alarm.setCreateTime(LocalDateTime.now());

        alarmService.save(alarm);
        pushAlarm(alarm);
        log.warn("Sensor abnormal alarm created, alarmId={}, plotId={}, deviceId={}, alarmType={}, triggerValue={}, thresholdValue={}",
                alarm.getId(),
                alarm.getPlotId(),
                alarm.getDeviceId(),
                alarm.getAlarmType(),
                alarm.getTriggerValue(),
                alarm.getThresholdValue());
        return true;
    }

    private boolean isAbnormal(BigDecimal value, BigDecimal minValue, BigDecimal maxValue) {
        return value != null
                && (value.compareTo(minValue) < 0 || value.compareTo(maxValue) > 0);
    }

    private void pushAlarm(Alarm alarm) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("id", alarm.getId());
            data.put("plotId", alarm.getPlotId());
            data.put("deviceId", alarm.getDeviceId());
            data.put("alarmType", alarm.getAlarmType());
            data.put("severity", alarm.getSeverity());
            data.put("triggerValue", alarm.getTriggerValue());
            data.put("thresholdValue", alarm.getThresholdValue());
            data.put("status", alarm.getStatus());
            data.put("message", alarm.getMessage());
            data.put("createTime", alarm.getCreateTime() == null ? null : alarm.getCreateTime().toString());

            Map<String, Object> message = new HashMap<>();
            message.put("type", "ALARM");
            message.put("plotId", alarm.getPlotId());
            message.put("data", data);

            realtimeWebSocketHandler.sendToAll(objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            log.warn("Push alarm websocket failed, alarmId={}", alarm.getId(), e);
        }
    }

    private int resolveConsecutiveThreshold(Integer consecutiveThreshold) {
        if (consecutiveThreshold == null || consecutiveThreshold <= 0) {
            return 3;
        }

        return consecutiveThreshold;
    }

    private boolean isContinuousLowMoisture(Long plotId, BigDecimal moistureMin, int consecutiveThreshold) {
        List<TelemetryData> recentList = telemetryService.list(
                new LambdaQueryWrapper<TelemetryData>()
                        .eq(TelemetryData::getPlotId, plotId)
                        .isNotNull(TelemetryData::getSoilMoisture)
                        .orderByDesc(TelemetryData::getCollectedAt)
                        .last("LIMIT " + consecutiveThreshold)
        );

        if (recentList.size() < consecutiveThreshold) {
            return false;
        }

        for (TelemetryData telemetry : recentList) {
            if (telemetry.getSoilMoisture() == null
                    || telemetry.getSoilMoisture().compareTo(moistureMin) >= 0) {
                return false;
            }
        }

        return true;
    }

    private String resolveMoistureSeverity(BigDecimal currentValue, BigDecimal thresholdValue) {
        if (currentValue == null || thresholdValue == null) {
            return "WARNING";
        }

        BigDecimal criticalLine = thresholdValue.subtract(new BigDecimal("10"));
        if (currentValue.compareTo(criticalLine) <= 0) {
            return "CRITICAL";
        }

        return "WARNING";
    }
}
