package com.agriculture.service.impl;

import com.agriculture.entity.ControlCommand;
import com.agriculture.entity.Device;
import com.agriculture.entity.IrrigationRecord;
import com.agriculture.entity.IrrigationStrategy;
import com.agriculture.entity.TelemetryData;
import com.agriculture.service.AutoIrrigationService;
import com.agriculture.service.ControlService;
import com.agriculture.service.DeviceService;
import com.agriculture.service.IrrigationStatsService;
import com.agriculture.service.IrrigationStrategyService;
import com.agriculture.service.TelemetryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AutoIrrigationServiceImpl implements AutoIrrigationService {

    private static final Logger log = LoggerFactory.getLogger(AutoIrrigationServiceImpl.class);
    private static final int RECENT_COMMAND_SECONDS = 60;

    private final IrrigationStrategyService irrigationStrategyService;
    private final IrrigationStatsService irrigationStatsService;
    private final TelemetryService telemetryService;
    private final DeviceService deviceService;
    private final ControlService controlService;

    public AutoIrrigationServiceImpl(IrrigationStrategyService irrigationStrategyService,
                                     IrrigationStatsService irrigationStatsService,
                                     TelemetryService telemetryService,
                                     DeviceService deviceService,
                                     ControlService controlService) {
        this.irrigationStrategyService = irrigationStrategyService;
        this.irrigationStatsService = irrigationStatsService;
        this.telemetryService = telemetryService;
        this.deviceService = deviceService;
        this.controlService = controlService;
    }

    @Override
    public void handleTelemetry(TelemetryData telemetryData) {
        if (telemetryData == null
                || telemetryData.getPlotId() == null
                || telemetryData.getDeviceId() == null
                || telemetryData.getSoilMoisture() == null) {
            return;
        }

        if (hasAbnormalTelemetryValue(telemetryData)) {
            log.warn("Auto irrigation skipped because telemetry is abnormal, plotId={}, deviceId={}, soilMoisture={}, airTemperature={}, airHumidity={}, illuminance={}",
                    telemetryData.getPlotId(),
                    telemetryData.getDeviceId(),
                    telemetryData.getSoilMoisture(),
                    telemetryData.getAirTemperature(),
                    telemetryData.getAirHumidity(),
                    telemetryData.getIlluminance());
            return;
        }

        IrrigationStrategy strategy = irrigationStrategyService.getByPlotId(telemetryData.getPlotId());
        if (strategy == null || Boolean.FALSE.equals(strategy.getAutoMode())) {
            return;
        }

        Device device = deviceService.getById(telemetryData.getDeviceId());
        if (device == null || device.getDeviceCode() == null || device.getDeviceCode().isBlank()) {
            return;
        }

        IrrigationRecord runningRecord = getLatestRunning(device.getDeviceCode());
        if (runningRecord != null) {
            stopIfReachedTarget(device, strategy, telemetryData.getSoilMoisture());
            return;
        }

        startIfContinuousLow(device, strategy, telemetryData);
    }

    @Override
    public void checkRunningIrrigationTimeouts() {
        List<IrrigationRecord> runningRecords = irrigationStatsService.list(
                new LambdaQueryWrapper<IrrigationRecord>()
                        .eq(IrrigationRecord::getStatus, "RUNNING")
                        .orderByAsc(IrrigationRecord::getStartTime)
        );

        LocalDateTime now = LocalDateTime.now();
        for (IrrigationRecord record : runningRecords) {
            if (record.getPlotId() == null || record.getDeviceCode() == null || record.getStartTime() == null) {
                continue;
            }

            IrrigationStrategy strategy = irrigationStrategyService.getByPlotId(record.getPlotId());
            if (strategy == null || strategy.getMaxDuration() == null || strategy.getMaxDuration() <= 0) {
                continue;
            }

            long runningSeconds = Duration.between(record.getStartTime(), now).getSeconds();
            if (runningSeconds < strategy.getMaxDuration()) {
                continue;
            }

            Device device = deviceService.getByDeviceCode(record.getDeviceCode());
            if (device == null || hasRecentCommand(record.getDeviceCode(), "PUMP_OFF")) {
                continue;
            }

            sendAutoCommand(record.getDeviceCode(), "PUMP_OFF", "OFF");
        }
    }

    private void stopIfReachedTarget(Device device, IrrigationStrategy strategy, BigDecimal soilMoisture) {
        BigDecimal moistureMax = strategy.getMoistureMax();
        if (moistureMax == null || soilMoisture.compareTo(moistureMax) < 0) {
            return;
        }

        if (hasRecentCommand(device.getDeviceCode(), "PUMP_OFF")) {
            return;
        }

        sendAutoCommand(device.getDeviceCode(), "PUMP_OFF", "OFF");
    }

    private void startIfContinuousLow(Device device, IrrigationStrategy strategy, TelemetryData telemetryData) {
        BigDecimal moistureMin = strategy.getMoistureMin();
        if (moistureMin == null || telemetryData.getSoilMoisture().compareTo(moistureMin) >= 0) {
            return;
        }

        int threshold = resolveConsecutiveThreshold(strategy.getConsecutiveThreshold());
        if (!isContinuousLowMoisture(telemetryData.getPlotId(), moistureMin, threshold)) {
            return;
        }

        if (isInCooldown(device.getDeviceCode(), strategy.getCooldownMinutes())
                || hasRecentCommand(device.getDeviceCode(), "PUMP_ON")) {
            return;
        }

        sendAutoCommand(device.getDeviceCode(), "PUMP_ON", "ON");
    }

    private void sendAutoCommand(String deviceCode, String commandType, String commandValue) {
        try {
            controlService.sendCommand(deviceCode, commandType, commandValue, "AUTO");
        } catch (IllegalArgumentException e) {
            log.warn("Auto irrigation command skipped, deviceCode={}, commandType={}, reason={}",
                    deviceCode, commandType, e.getMessage());
        }
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

    private boolean isInCooldown(String deviceCode, Integer cooldownMinutes) {
        int minutes = cooldownMinutes == null ? 10 : cooldownMinutes;
        if (minutes <= 0) {
            return false;
        }

        IrrigationRecord lastFinished = irrigationStatsService.getOne(
                new LambdaQueryWrapper<IrrigationRecord>()
                        .eq(IrrigationRecord::getDeviceCode, deviceCode)
                        .eq(IrrigationRecord::getStatus, "FINISHED")
                        .isNotNull(IrrigationRecord::getEndTime)
                        .orderByDesc(IrrigationRecord::getEndTime)
                        .last("LIMIT 1"),
                false
        );

        return lastFinished != null
                && lastFinished.getEndTime() != null
                && lastFinished.getEndTime().plusMinutes(minutes).isAfter(LocalDateTime.now());
    }

    private IrrigationRecord getLatestRunning(String deviceCode) {
        return irrigationStatsService.getOne(
                new LambdaQueryWrapper<IrrigationRecord>()
                        .eq(IrrigationRecord::getDeviceCode, deviceCode)
                        .eq(IrrigationRecord::getStatus, "RUNNING")
                        .orderByDesc(IrrigationRecord::getStartTime)
                        .last("LIMIT 1"),
                false
        );
    }

    private boolean hasRecentCommand(String deviceCode, String commandType) {
        LocalDateTime after = LocalDateTime.now().minusSeconds(RECENT_COMMAND_SECONDS);
        return controlService.count(
                new LambdaQueryWrapper<ControlCommand>()
                        .eq(ControlCommand::getDeviceCode, deviceCode)
                        .eq(ControlCommand::getCommandType, commandType)
                        .in(ControlCommand::getStatus, "PENDING", "SENT", "SUCCESS")
                        .ge(ControlCommand::getCreatedAt, after)
        ) > 0;
    }

    private boolean hasAbnormalTelemetryValue(TelemetryData telemetry) {
        return isOutOfRange(telemetry.getSoilMoisture(), BigDecimal.ZERO, new BigDecimal("100"))
                || isOutOfRange(telemetry.getAirTemperature(), new BigDecimal("-10"), new BigDecimal("80"))
                || isOutOfRange(telemetry.getAirHumidity(), BigDecimal.ZERO, new BigDecimal("100"))
                || isOutOfRange(telemetry.getIlluminance(), BigDecimal.ZERO, new BigDecimal("1000"));
    }

    private boolean isOutOfRange(BigDecimal value, BigDecimal minValue, BigDecimal maxValue) {
        return value != null && (value.compareTo(minValue) < 0 || value.compareTo(maxValue) > 0);
    }

    private int resolveConsecutiveThreshold(Integer consecutiveThreshold) {
        if (consecutiveThreshold == null || consecutiveThreshold <= 0) {
            return 3;
        }
        return consecutiveThreshold;
    }
}
