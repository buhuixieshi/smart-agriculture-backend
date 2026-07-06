package com.agriculture.service.impl;

import com.agriculture.config.HardwareControlProperties;
import com.agriculture.dto.IrrigationControlDTO;
import com.agriculture.entity.ControlCommand;
import com.agriculture.entity.Device;
import com.agriculture.mapper.ControlCommandMapper;
import com.agriculture.service.CommandDispatchResult;
import com.agriculture.service.ControlService;
import com.agriculture.service.DeviceService;
import com.agriculture.service.HardwareControlClient;
import com.agriculture.service.IrrigationStatsService;
import com.agriculture.service.MqttCommandService;
import com.agriculture.service.WaterUsageLimitService;
import com.agriculture.vo.CommandVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ControlServiceImpl extends ServiceImpl<ControlCommandMapper, ControlCommand> implements ControlService {

    private final DeviceService deviceService;
    private final MqttCommandService mqttCommandService;
    private final HardwareControlClient hardwareControlClient;
    private final HardwareControlProperties hardwareControlProperties;
    private final IrrigationStatsService irrigationStatsService;
    private final WaterUsageLimitService waterUsageLimitService;

    public ControlServiceImpl(DeviceService deviceService,
                              MqttCommandService mqttCommandService,
                              HardwareControlClient hardwareControlClient,
                              HardwareControlProperties hardwareControlProperties,
                              IrrigationStatsService irrigationStatsService,
                              WaterUsageLimitService waterUsageLimitService) {
        this.deviceService = deviceService;
        this.mqttCommandService = mqttCommandService;
        this.hardwareControlClient = hardwareControlClient;
        this.hardwareControlProperties = hardwareControlProperties;
        this.irrigationStatsService = irrigationStatsService;
        this.waterUsageLimitService = waterUsageLimitService;
    }

    @Override
    @Transactional
    public ControlCommand sendCommand(String deviceCode, String commandType, String commandValue) {
        return sendCommand(deviceCode, commandType, commandValue, "WEB");
    }

    @Override
    @Transactional
    public ControlCommand sendCommand(String deviceCode, String commandType, String commandValue, String requestSource) {
        validateCommandType(commandType);

        Device device = deviceService.getByDeviceCode(deviceCode);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在：" + deviceCode);
        }

        if ("PUMP_ON".equals(commandType)) {
            waterUsageLimitService.checkBeforePumpOn(device);
        }

        LocalDateTime now = LocalDateTime.now();

        ControlCommand command = new ControlCommand();
        command.setCommandNo(generateCommandNo());
        command.setPlotId(device.getPlotId());
        command.setDeviceId(device.getId());
        command.setDeviceCode(device.getDeviceCode());
        command.setCommandType(commandType);
        command.setCommandValue(normalizeCommandValue(commandType, commandValue));
        command.setStatus("PENDING");
        command.setRequestSource(resolveRequestSource(requestSource));
        command.setCreatedAt(now);
        command.setUpdatedAt(now);

        this.save(command);

        try {
            if (Boolean.TRUE.equals(hardwareControlProperties.getEnabled())) {
                sendByHardwareGateway(device, command);
            } else {
                sendByMqtt(device, command);
            }
        } catch (Exception e) {
            command.setStatus("FAILED");
            command.setErrorMessage(e.getMessage());
            command.setUpdatedAt(LocalDateTime.now());
            this.updateById(command);
        }

        return command;
    }

    private String resolveRequestSource(String requestSource) {
        if (requestSource == null || requestSource.isBlank()) {
            return "WEB";
        }
        return requestSource;
    }

    private void sendByHardwareGateway(Device device, ControlCommand command) {
        LocalDateTime sentAt = LocalDateTime.now();
        command.setSentAt(sentAt);

        String message = hardwareControlClient.sendCommand(command);
        LocalDateTime ackAt = LocalDateTime.now();

        command.setStatus("SUCCESS");
        command.setAckAt(ackAt);
        command.setErrorMessage(message);
        command.setUpdatedAt(ackAt);
        this.updateById(command);

        handleSuccessfulIrrigationRecord(device, command);
    }

    private void sendByMqtt(Device device, ControlCommand command) {
        CommandDispatchResult result = mqttCommandService.sendCommand(command);

        command.setSentAt(LocalDateTime.now());
        if (result.acknowledged()) {
            command.setStatus("SUCCESS");
            command.setAckAt(LocalDateTime.now());
            command.setErrorMessage(result.message());
        } else {
            command.setStatus("SENT");
            command.setErrorMessage(result.message());
        }
        command.setUpdatedAt(LocalDateTime.now());
        this.updateById(command);

        if (result.acknowledged()) {
            handleSuccessfulIrrigationRecord(device, command);
        }
    }

    private void handleSuccessfulIrrigationRecord(Device device, ControlCommand command) {
        if ("PUMP_ON".equals(command.getCommandType())) {
            irrigationStatsService.startIrrigation(device, command);
        } else if ("PUMP_OFF".equals(command.getCommandType())) {
            waterUsageLimitService.checkFinishedRecord(device, irrigationStatsService.finishLatestRunning(device, command));
        }
    }

    @Override
    public List<ControlCommand> listByDeviceCode(String deviceCode) {
        LambdaQueryWrapper<ControlCommand> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ControlCommand::getDeviceCode, deviceCode)
                .orderByDesc(ControlCommand::getCreatedAt);

        return this.list(wrapper);
    }

    @Override
    @Transactional
    public CommandVO irrigationControl(IrrigationControlDTO dto) {
        String action = dto.getAction();

        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action为必填参数");
        }

        String commandType;
        String commandValue;

        if ("ON".equalsIgnoreCase(action)) {
            commandType = "PUMP_ON";
            commandValue = "ON";
        } else if ("OFF".equalsIgnoreCase(action)) {
            commandType = "PUMP_OFF";
            commandValue = "OFF";
        } else {
            throw new IllegalArgumentException("action只支持ON或OFF");
        }

        ControlCommand command = sendCommand(dto.getDeviceCode(), commandType, commandValue);
        return toCommandVO(command);
    }

    @Override
    public CommandVO getCommandStatus(String commandNo) {
        if (commandNo == null || commandNo.isBlank()) {
            throw new IllegalArgumentException("commandNo为必填参数");
        }

        LambdaQueryWrapper<ControlCommand> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ControlCommand::getCommandNo, commandNo);

        ControlCommand command = this.getOne(wrapper);
        if (command == null) {
            throw new IllegalArgumentException("命令不存在：" + commandNo);
        }

        return toCommandVO(command);
    }

    private void validateCommandType(String commandType) {
        if (!"PUMP_ON".equals(commandType)
                && !"PUMP_OFF".equals(commandType)
                && !"LIGHT_ON".equals(commandType)
                && !"LIGHT_OFF".equals(commandType)) {
            throw new IllegalArgumentException("不支持的命令类型：" + commandType);
        }
    }

    private String normalizeCommandValue(String commandType, String commandValue) {
        if (commandValue != null && !commandValue.isBlank()) {
            return commandValue;
        }

        if ("PUMP_ON".equals(commandType) || "LIGHT_ON".equals(commandType)) {
            return "ON";
        }

        if ("PUMP_OFF".equals(commandType) || "LIGHT_OFF".equals(commandType)) {
            return "OFF";
        }

        return commandValue;
    }

    private String generateCommandNo() {
        return "CMD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
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
