package com.agriculture.service.impl;

import com.agriculture.dto.LightControlDTO;
import com.agriculture.entity.ControlCommand;
import com.agriculture.entity.Device;
import com.agriculture.entity.LightStrategy;
import com.agriculture.entity.TelemetryData;
import com.agriculture.service.ControlService;
import com.agriculture.service.DeviceService;
import com.agriculture.service.LightControlService;
import com.agriculture.service.LightStrategyService;
import com.agriculture.service.TelemetryService;
import com.agriculture.vo.CommandVO;
import com.agriculture.vo.LightStatusVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class LightControlServiceImpl implements LightControlService {

    private static final BigDecimal DEFAULT_ILLUMINANCE_MIN = new BigDecimal("500.00");
    private static final BigDecimal DEFAULT_ILLUMINANCE_MAX = new BigDecimal("800.00");
    private static final int DEFAULT_COOLDOWN_MINUTES = 5;
    private static final String REAL_BEARPI_DEVICE_CODE = "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001";

    private final DeviceService deviceService;
    private final TelemetryService telemetryService;
    private final ControlService controlService;
    private final LightStrategyService lightStrategyService;

    public LightControlServiceImpl(DeviceService deviceService,
                                   TelemetryService telemetryService,
                                   ControlService controlService,
                                   LightStrategyService lightStrategyService) {
        this.deviceService = deviceService;
        this.telemetryService = telemetryService;
        this.controlService = controlService;
        this.lightStrategyService = lightStrategyService;
    }

    @Override
    @Transactional
    public CommandVO control(LightControlDTO dto) {
        Device device = deviceService.getByDeviceCode(dto.getDeviceCode());
        if (device == null) {
            throw new IllegalArgumentException("设备不存在：" + dto.getDeviceCode());
        }

        if (!isLightCapable(device)) {
            throw new IllegalArgumentException("该设备不是补光控制设备：" + dto.getDeviceCode());
        }

        String action = dto.getAction();
        if ("ON".equalsIgnoreCase(action)) {
            checkManualLightOnSafety(device, Boolean.TRUE.equals(dto.getForce()));
            return toCommandVO(controlService.sendCommand(device.getDeviceCode(), "LIGHT_ON", "ON", "WEB"));
        }
        if ("OFF".equalsIgnoreCase(action)) {
            return toCommandVO(controlService.sendCommand(device.getDeviceCode(), "LIGHT_OFF", "OFF", "WEB"));
        }

        throw new IllegalArgumentException("action只支持ON或OFF");
    }

    @Override
    public void handleTelemetry(TelemetryData telemetry) {
        try {
            doHandleTelemetry(telemetry);
        } catch (Exception ignored) {
            // Automatic lighting must not interrupt telemetry scanning or alarm processing.
        }
    }

    @Override
    public LightStatusVO getStatus(Long plotId, String deviceCode) {
        Device device = resolveDevice(plotId, deviceCode);
        Long targetPlotId = device == null ? plotId : device.getPlotId();
        TelemetryData latest = targetPlotId == null ? null : telemetryService.getLatestByPlotId(targetPlotId);
        LightStrategy strategy = safeStrategy(targetPlotId);
        ControlCommand latestCommand = device == null ? null : latestLightCommand(device.getDeviceCode());

        LightStatusVO vo = new LightStatusVO();
        vo.setPlotId(targetPlotId);
        if (device != null) {
            vo.setDeviceId(device.getId());
            vo.setDeviceCode(device.getDeviceCode());
            vo.setDeviceName(device.getDeviceName());
            vo.setDeviceType(device.getDeviceType());
            vo.setLightControllable(isLightCapable(device));
        }
        if (latest != null) {
            vo.setIlluminance(latest.getIlluminance());
            vo.setLightStatus(latest.getLightStatus());
            vo.setCollectedAt(latest.getCollectedAt());
        }
        if (strategy != null) {
            vo.setAutoMode(strategy.getAutoMode());
            vo.setIlluminanceMin(strategy.getIlluminanceMin());
            vo.setIlluminanceMax(strategy.getIlluminanceMax());
        }
        if (latestCommand != null) {
            vo.setLatestCommandStatus(latestCommand.getStatus());
        }
        return vo;
    }

    private void doHandleTelemetry(TelemetryData telemetry) {
        if (telemetry == null || telemetry.getPlotId() == null || telemetry.getIlluminance() == null) {
            return;
        }

        LightStrategy strategy = safeStrategy(telemetry.getPlotId());
        if (strategy == null || !Boolean.TRUE.equals(strategy.getAutoMode())) {
            return;
        }

        Device device = resolveTelemetryDevice(telemetry);
        if (device == null || device.getDeviceCode() == null) {
            return;
        }

        int cooldownMinutes = strategy.getCooldownMinutes() == null ? DEFAULT_COOLDOWN_MINUTES : strategy.getCooldownMinutes();
        if (hasRecentLightCommand(device.getDeviceCode(), cooldownMinutes)) {
            return;
        }

        BigDecimal illuminance = telemetry.getIlluminance();
        BigDecimal min = strategy.getIlluminanceMin() == null ? DEFAULT_ILLUMINANCE_MIN : strategy.getIlluminanceMin();
        BigDecimal max = strategy.getIlluminanceMax() == null ? DEFAULT_ILLUMINANCE_MAX : strategy.getIlluminanceMax();
        boolean inWindow = isInActiveWindow(strategy, LocalTime.now());

        if (!inWindow) {
            if ("ON".equalsIgnoreCase(telemetry.getLightStatus())) {
                controlService.sendCommand(device.getDeviceCode(), "LIGHT_OFF", "OFF", "AUTO");
            }
            return;
        }

        if (illuminance.compareTo(min) < 0 && !"ON".equalsIgnoreCase(telemetry.getLightStatus())) {
            controlService.sendCommand(device.getDeviceCode(), "LIGHT_ON", "ON", "AUTO");
        } else if (illuminance.compareTo(max) >= 0 && !"OFF".equalsIgnoreCase(telemetry.getLightStatus())) {
            controlService.sendCommand(device.getDeviceCode(), "LIGHT_OFF", "OFF", "AUTO");
        }
    }

    private void checkManualLightOnSafety(Device device, boolean force) {
        if (force || device.getPlotId() == null) {
            return;
        }

        TelemetryData latest = telemetryService.getLatestByPlotId(device.getPlotId());
        if (latest == null || latest.getIlluminance() == null) {
            return;
        }

        LightStrategy strategy = safeStrategy(device.getPlotId());
        BigDecimal max = strategy == null || strategy.getIlluminanceMax() == null
                ? DEFAULT_ILLUMINANCE_MAX
                : strategy.getIlluminanceMax();
        if (latest.getIlluminance().compareTo(max) >= 0) {
            throw new IllegalArgumentException("当前光照充足，禁止开灯；如需强制开灯请传force=true");
        }
    }

    private Device resolveDevice(Long plotId, String deviceCode) {
        if (deviceCode != null && !deviceCode.isBlank()) {
            return deviceService.getByDeviceCode(deviceCode);
        }
        if (plotId == null) {
            return null;
        }
        return deviceService.listByPlotId(plotId).stream()
                .filter(this::isLightCapable)
                .sorted((left, right) -> Integer.compare(lightPriority(right), lightPriority(left)))
                .findFirst()
                .orElseGet(() -> deviceService.getOne(
                        new LambdaQueryWrapper<Device>()
                                .eq(Device::getPlotId, plotId)
                                .orderByDesc(Device::getId)
                                .last("LIMIT 1"),
                        false
                ));
    }

    private Device resolveTelemetryDevice(TelemetryData telemetry) {
        if (telemetry.getDeviceId() != null) {
            Device device = deviceService.getById(telemetry.getDeviceId());
            if (device != null) {
                return device;
            }
        }
        return resolveDevice(telemetry.getPlotId(), null);
    }

    private boolean isLightCapable(Device device) {
        if (device == null) {
            return false;
        }
        String deviceCode = device.getDeviceCode() == null ? "" : device.getDeviceCode();
        String deviceType = device.getDeviceType() == null ? "" : device.getDeviceType().toUpperCase();
        return REAL_BEARPI_DEVICE_CODE.equals(deviceCode)
                || deviceType.contains("LIGHT")
                || deviceType.contains("LAMP")
                || deviceType.contains("BEARPI");
    }

    private int lightPriority(Device device) {
        String deviceCode = device.getDeviceCode() == null ? "" : device.getDeviceCode();
        String deviceType = device.getDeviceType() == null ? "" : device.getDeviceType().toUpperCase();
        if (REAL_BEARPI_DEVICE_CODE.equals(deviceCode)) {
            return 100;
        }
        if (deviceType.contains("LIGHT") || deviceType.contains("LAMP")) {
            return 90;
        }
        if ("BEARPI".equals(deviceType)) {
            return 80;
        }
        if (deviceType.contains("BEARPI")) {
            return 70;
        }
        return 0;
    }

    private LightStrategy safeStrategy(Long plotId) {
        if (plotId == null) {
            return null;
        }
        try {
            return lightStrategyService.getOrCreateDefault(plotId);
        } catch (DataAccessException e) {
            LightStrategy fallback = new LightStrategy();
            fallback.setPlotId(plotId);
            fallback.setIlluminanceMin(DEFAULT_ILLUMINANCE_MIN);
            fallback.setIlluminanceMax(DEFAULT_ILLUMINANCE_MAX);
            fallback.setAutoMode(true);
            fallback.setStartTime(LocalTime.of(6, 0));
            fallback.setEndTime(LocalTime.of(20, 0));
            fallback.setCooldownMinutes(DEFAULT_COOLDOWN_MINUTES);
            return fallback;
        }
    }

    private boolean isInActiveWindow(LightStrategy strategy, LocalTime now) {
        LocalTime start = strategy.getStartTime();
        LocalTime end = strategy.getEndTime();
        if (start == null || end == null || start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        }
        return !now.isBefore(start) || !now.isAfter(end);
    }

    private boolean hasRecentLightCommand(String deviceCode, int cooldownMinutes) {
        LocalDateTime from = LocalDateTime.now().minusMinutes(Math.max(1, cooldownMinutes));
        return controlService.count(
                new LambdaQueryWrapper<ControlCommand>()
                        .eq(ControlCommand::getDeviceCode, deviceCode)
                        .in(ControlCommand::getCommandType, "LIGHT_ON", "LIGHT_OFF")
                        .ge(ControlCommand::getCreatedAt, from)
        ) > 0;
    }

    private ControlCommand latestLightCommand(String deviceCode) {
        return controlService.getOne(
                new LambdaQueryWrapper<ControlCommand>()
                        .eq(ControlCommand::getDeviceCode, deviceCode)
                        .in(ControlCommand::getCommandType, "LIGHT_ON", "LIGHT_OFF")
                        .orderByDesc(ControlCommand::getCreatedAt)
                        .last("LIMIT 1"),
                false
        );
    }

    private CommandVO toCommandVO(ControlCommand command) {
        CommandVO vo = new CommandVO();
        vo.setId(command.getId());
        vo.setCommandNo(command.getCommandNo());
        vo.setPlotId(command.getPlotId());
        vo.setDeviceId(command.getDeviceId());
        vo.setDeviceCode(command.getDeviceCode());
        vo.setCommandType(command.getCommandType());
        vo.setCommandValue(command.getCommandValue());
        vo.setStatus(command.getStatus());
        vo.setRequestSource(command.getRequestSource());
        vo.setErrorMessage(command.getErrorMessage());
        vo.setSentAt(command.getSentAt());
        vo.setAckAt(command.getAckAt());
        vo.setCreatedAt(command.getCreatedAt());
        vo.setUpdatedAt(command.getUpdatedAt());
        return vo;
    }
}
