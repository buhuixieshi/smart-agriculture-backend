package com.agriculture.service.impl;

import com.agriculture.entity.Alarm;
import com.agriculture.entity.Device;
import com.agriculture.entity.IrrigationStrategy;
import com.agriculture.entity.TelemetryData;
import com.agriculture.service.AlarmRuleService;
import com.agriculture.service.AlarmService;
import com.agriculture.service.IrrigationStrategyService;
import com.agriculture.service.TelemetryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlarmRuleServiceImpl implements AlarmRuleService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String LOW_SOIL_MOISTURE = "LOW_SOIL_MOISTURE";
    private static final String DEVICE_OFFLINE = "DEVICE_OFFLINE";

    private final AlarmService alarmService;
    private final IrrigationStrategyService irrigationStrategyService;
    private final TelemetryService telemetryService;

    public AlarmRuleServiceImpl(AlarmService alarmService,
                                IrrigationStrategyService irrigationStrategyService,
                                TelemetryService telemetryService) {
        this.alarmService = alarmService;
        this.irrigationStrategyService = irrigationStrategyService;
        this.telemetryService = telemetryService;
    }

    @Override
    public void checkTelemetry(TelemetryData telemetryData) {
        if (telemetryData == null
                || telemetryData.getPlotId() == null
                || telemetryData.getDeviceId() == null
                || telemetryData.getSoilMoisture() == null) {
            return;
        }

        IrrigationStrategy strategy = irrigationStrategyService.getOne(
                new LambdaQueryWrapper<IrrigationStrategy>()
                        .eq(IrrigationStrategy::getPlotId, telemetryData.getPlotId())
                        .last("LIMIT 1"),
                false
        );

        if (strategy == null || Boolean.FALSE.equals(strategy.getAutoMode())) {
            return;
        }

        BigDecimal moistureMin = strategy.getMoistureMin();
        if (moistureMin == null) {
            return;
        }

        int threshold = resolveConsecutiveThreshold(strategy.getConsecutiveThreshold());
        if (!isContinuousLowMoisture(telemetryData.getPlotId(), moistureMin, threshold)) {
            return;
        }

        boolean existsActiveAlarm = alarmService.count(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getPlotId, telemetryData.getPlotId())
                        .eq(Alarm::getDeviceId, telemetryData.getDeviceId())
                        .eq(Alarm::getAlarmType, LOW_SOIL_MOISTURE)
                        .eq(Alarm::getStatus, STATUS_ACTIVE)
        ) > 0;

        if (existsActiveAlarm) {
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
        alarm.setMessage("Soil moisture stayed below threshold.");
        alarm.setCreateTime(LocalDateTime.now());

        alarmService.save(alarm);
    }

    @Override
    public void handleDeviceOffline(Device device) {
        if (device == null || device.getId() == null) {
            return;
        }

        boolean existsActiveAlarm = alarmService.count(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getDeviceId, device.getId())
                        .eq(Alarm::getAlarmType, DEVICE_OFFLINE)
                        .eq(Alarm::getStatus, STATUS_ACTIVE)
        ) > 0;

        if (existsActiveAlarm) {
            return;
        }

        Alarm alarm = new Alarm();
        alarm.setPlotId(device.getPlotId());
        alarm.setDeviceId(device.getId());
        alarm.setAlarmType(DEVICE_OFFLINE);
        alarm.setSeverity("CRITICAL");
        alarm.setStatus(STATUS_ACTIVE);
        alarm.setMessage("Device heartbeat timed out.");
        alarm.setCreateTime(LocalDateTime.now());

        alarmService.save(alarm);
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
