package com.agriculture.service.impl;

import com.agriculture.entity.ControlCommand;
import com.agriculture.service.MqttCommandService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "false")
public class DisabledMqttCommandServiceImpl implements MqttCommandService {

    @Override
    public void sendCommand(ControlCommand command) {
        // Used by tests or local runs where MQTT integration is explicitly disabled.
    }
}
