package com.agriculture.task;

import com.agriculture.entity.Device;
import com.agriculture.entity.TelemetryData;
import com.agriculture.service.AlarmRuleService;
import com.agriculture.service.AutoIrrigationService;
import com.agriculture.service.DeviceService;
import com.agriculture.service.LightControlService;
import com.agriculture.service.TelemetryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TelemetryRuleScanTask {

    private static final int BATCH_SIZE = 100;

    private final TelemetryService telemetryService;
    private final AlarmRuleService alarmRuleService;
    private final AutoIrrigationService autoIrrigationService;
    private final LightControlService lightControlService;
    private final DeviceService deviceService;

    private boolean initialized;
    private long lastProcessedId;

    public TelemetryRuleScanTask(TelemetryService telemetryService,
                                 AlarmRuleService alarmRuleService,
                                 AutoIrrigationService autoIrrigationService,
                                 LightControlService lightControlService,
                                 DeviceService deviceService) {
        this.telemetryService = telemetryService;
        this.alarmRuleService = alarmRuleService;
        this.autoIrrigationService = autoIrrigationService;
        this.lightControlService = lightControlService;
        this.deviceService = deviceService;
    }

    @Scheduled(fixedDelay = 10000)
    public void scanNewTelemetry() {
        initializeCursorIfNeeded();

        List<TelemetryData> telemetryList = telemetryService.list(
                new LambdaQueryWrapper<TelemetryData>()
                        .gt(TelemetryData::getId, lastProcessedId)
                        .orderByAsc(TelemetryData::getId)
                        .last("LIMIT " + BATCH_SIZE)
        );

        for (TelemetryData telemetry : telemetryList) {
            enrichDeviceIdIfMissing(telemetry);
            alarmRuleService.checkTelemetry(telemetry);
            autoIrrigationService.handleTelemetry(telemetry);
            lightControlService.handleTelemetry(telemetry);
            if (telemetry.getId() != null && telemetry.getId() > lastProcessedId) {
                lastProcessedId = telemetry.getId();
            }
        }

        autoIrrigationService.checkRunningIrrigationTimeouts();
    }

    private void initializeCursorIfNeeded() {
        if (initialized) {
            return;
        }

        TelemetryData latest = telemetryService.getOne(
                new LambdaQueryWrapper<TelemetryData>()
                        .orderByDesc(TelemetryData::getId)
                        .last("LIMIT 1"),
                false
        );
        if (latest != null && latest.getId() != null) {
            lastProcessedId = latest.getId();
        }
        initialized = true;
    }

    private void enrichDeviceIdIfMissing(TelemetryData telemetry) {
        if (telemetry.getDeviceId() != null || telemetry.getPlotId() == null) {
            return;
        }

        Device device = deviceService.getOne(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getPlotId, telemetry.getPlotId())
                        .orderByDesc(Device::getId)
                        .last("LIMIT 1"),
                false
        );
        if (device != null) {
            telemetry.setDeviceId(device.getId());
        }
    }
}
