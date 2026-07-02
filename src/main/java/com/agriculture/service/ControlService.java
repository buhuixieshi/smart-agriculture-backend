package com.agriculture.service;

import com.agriculture.entity.ControlCommand;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ControlService extends IService<ControlCommand> {

    ControlCommand sendCommand(String deviceCode, String commandType, String commandValue);

    List<ControlCommand> listByDeviceCode(String deviceCode);
}
