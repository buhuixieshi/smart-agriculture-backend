package com.agriculture.service;

import com.agriculture.entity.ControlCommand;

public interface MqttCommandService {

    void sendCommand(ControlCommand command);
}
