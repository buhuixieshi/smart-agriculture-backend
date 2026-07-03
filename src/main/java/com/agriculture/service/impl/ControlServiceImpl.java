package com.agriculture.service.impl;

import com.agriculture.dto.IrrigationControlDTO;
import com.agriculture.entity.ControlCommand;
import com.agriculture.entity.Device;
import com.agriculture.mapper.ControlCommandMapper;
import com.agriculture.service.ControlService;
import com.agriculture.service.DeviceService;
import com.agriculture.service.IrrigationStatsService;
import com.agriculture.service.MqttCommandService;
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
    private final IrrigationStatsService irrigationStatsService;
    private final MqttCommandService mqttCommandService;

    public ControlServiceImpl(DeviceService deviceService,
                              IrrigationStatsService irrigationStatsService,
                              MqttCommandService mqttCommandService) {
        this.deviceService = deviceService;
        this.irrigationStatsService = irrigationStatsService;
        this.mqttCommandService = mqttCommandService;
    }

    @Override
    @Transactional
    public ControlCommand sendCommand(String deviceCode, String commandType, String commandValue) {
        validateCommandType(commandType);

        Device device = deviceService.getByDeviceCode(deviceCode);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在：" + deviceCode);
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
        command.setRequestSource("WEB");
        command.setCreatedAt(now);
        command.setUpdatedAt(now);

        this.save(command);

        try {
            mqttCommandService.sendCommand(command);

            command.setStatus("SENT");
            command.setSentAt(LocalDateTime.now());
            command.setUpdatedAt(LocalDateTime.now());
            this.updateById(command);

            handleIrrigationRecord(device, command);
        } catch (Exception e) {
            command.setStatus("FAILED");
            command.setErrorMessage(e.getMessage());
            command.setUpdatedAt(LocalDateTime.now());
            this.updateById(command);
        }

        return command;
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

    private void handleIrrigationRecord(Device device, ControlCommand command) {
        if ("PUMP_ON".equals(command.getCommandType())) {
            irrigationStatsService.startIrrigation(device, command);
        } else if ("PUMP_OFF".equals(command.getCommandType())) {
            irrigationStatsService.finishLatestRunning(device, command);
        }
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
