package com.agriculture.service;

import com.agriculture.entity.ControlCommand;

public interface HardwareControlClient {

    String sendCommand(ControlCommand command);
}
