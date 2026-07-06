package com.agriculture.service;

import com.agriculture.entity.ControlCommand;

public interface MqttCommandService {

    CommandDispatchResult sendCommand(ControlCommand command);
}
