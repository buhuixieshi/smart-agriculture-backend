package com.agriculture.service;

import com.agriculture.dto.IrrigationControlDTO;
import com.agriculture.entity.ControlCommand;
import com.agriculture.vo.CommandVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ControlService extends IService<ControlCommand> {

    ControlCommand sendCommand(String deviceCode, String commandType, String commandValue);

    ControlCommand sendCommand(String deviceCode, String commandType, String commandValue, String requestSource);

    ControlCommand sendCommand(String deviceCode, String commandType, String commandValue, String requestSource,
                               Integer durationSeconds, Integer brightness);

    List<ControlCommand> listByDeviceCode(String deviceCode);

    CommandVO irrigationControl(IrrigationControlDTO dto);

    CommandVO getCommandStatus(String commandNo);
}
