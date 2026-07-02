package com.agriculture.service.impl;

import com.agriculture.entity.ControlCommand;
import com.agriculture.entity.Device;
import com.agriculture.mapper.ControlCommandMapper;
import com.agriculture.service.ControlService;
import com.agriculture.service.DeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ControlServiceImpl extends ServiceImpl<ControlCommandMapper, ControlCommand> implements ControlService {

    private final DeviceService deviceService;

    public ControlServiceImpl(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Override
    public ControlCommand sendCommand(String deviceCode, String commandType, String commandValue) {
        Device device = deviceService.getByDeviceCode(deviceCode);

        ControlCommand command = new ControlCommand();
        command.setCommandNo(generateCommandNo());
        command.setDeviceCode(deviceCode);
        command.setCommandType(commandType);
        command.setCommandValue(commandValue);
        command.setStatus("PENDING");
        command.setRequestSource("WEB");

        if (device != null) {
            command.setDeviceId(device.getId());
            command.setPlotId(device.getPlotId());
        }

        this.save(command);
        return command;
    }

    @Override
    public List<ControlCommand> listByDeviceCode(String deviceCode) {
        LambdaQueryWrapper<ControlCommand> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ControlCommand::getDeviceCode, deviceCode)
                .orderByDesc(ControlCommand::getCreatedAt);

        return this.list(wrapper);
    }

    private String generateCommandNo() {
        return "CMD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }
}
