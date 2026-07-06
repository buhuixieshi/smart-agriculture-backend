package com.agriculture.task;

import com.agriculture.entity.Device;
import com.agriculture.service.AlarmRuleService;
import com.agriculture.service.DeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeviceOfflineCheckTask {

    private static final Logger log = LoggerFactory.getLogger(DeviceOfflineCheckTask.class);

    private final DeviceService deviceService;
    private final AlarmRuleService alarmRuleService;

    public DeviceOfflineCheckTask(DeviceService deviceService,
                                  AlarmRuleService alarmRuleService) {
        this.deviceService = deviceService;
        this.alarmRuleService = alarmRuleService;
    }

    @Scheduled(fixedDelay = 30000)
    public void checkOfflineDevices() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(90);

        List<Device> offlineDevices = deviceService.list(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getStatus, "ONLINE")
                        .and(wrapper -> wrapper.isNull(Device::getLastHeartbeat)
                                .or()
                                .lt(Device::getLastHeartbeat, deadline))
        );

        for (Device device : offlineDevices) {
            LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Device::getId, device.getId())
                    .set(Device::getStatus, "OFFLINE")
                    .set(Device::getUpdatedAt, LocalDateTime.now());

            deviceService.update(wrapper);
            alarmRuleService.handleDeviceOffline(device);
            log.info("Device marked offline, deviceCode={}", device.getDeviceCode());
        }
    }
}
