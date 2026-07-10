package com.agriculture.task;

import com.agriculture.entity.Device;
import com.agriculture.service.AlarmRuleService;
import com.agriculture.service.DeviceService;
import com.agriculture.websocket.RealtimeWebSocketHandler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeviceOfflineCheckTask {

    private static final Logger log = LoggerFactory.getLogger(DeviceOfflineCheckTask.class);

    private final DeviceService deviceService;
    private final AlarmRuleService alarmRuleService;
    private final RealtimeWebSocketHandler realtimeWebSocketHandler;
    private final ObjectMapper objectMapper;

    public DeviceOfflineCheckTask(DeviceService deviceService,
                                  AlarmRuleService alarmRuleService,
                                  RealtimeWebSocketHandler realtimeWebSocketHandler,
                                  ObjectMapper objectMapper) {
        this.deviceService = deviceService;
        this.alarmRuleService = alarmRuleService;
        this.realtimeWebSocketHandler = realtimeWebSocketHandler;
        this.objectMapper = objectMapper;
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
            pushDeviceOffline(device);
            log.info("Device marked offline, deviceCode={}", device.getDeviceCode());
        }
    }

    private void pushDeviceOffline(Device device) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("deviceId", device.getId());
            data.put("deviceCode", device.getDeviceCode());
            data.put("plotId", device.getPlotId());
            data.put("status", "OFFLINE");
            data.put("lastHeartbeat", device.getLastHeartbeat() == null ? null : device.getLastHeartbeat().toString());

            Map<String, Object> message = new HashMap<>();
            message.put("type", "DEVICE_STATUS");
            message.put("plotId", device.getPlotId());
            message.put("data", data);

            realtimeWebSocketHandler.sendToAll(objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            log.warn("Push offline device status failed, deviceCode={}", device.getDeviceCode(), e);
        }
    }
}
