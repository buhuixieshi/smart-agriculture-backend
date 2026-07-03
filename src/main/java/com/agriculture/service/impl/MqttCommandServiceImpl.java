package com.agriculture.service.impl;

import com.agriculture.entity.ControlCommand;
import com.agriculture.mqtt.MqttGateway;
import com.agriculture.mqtt.MqttProperties;
import com.agriculture.service.MqttCommandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttCommandServiceImpl implements MqttCommandService {

    private final MqttGateway mqttGateway;
    private final MqttProperties mqttProperties;
    private final ObjectMapper objectMapper;

    public MqttCommandServiceImpl(MqttGateway mqttGateway,
                                  MqttProperties mqttProperties,
                                  ObjectMapper objectMapper) {
        this.mqttGateway = mqttGateway;
        this.mqttProperties = mqttProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sendCommand(ControlCommand command) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("commandNo", command.getCommandNo());
            payload.put("deviceCode", command.getDeviceCode());
            payload.put("commandType", command.getCommandType());
            payload.put("commandValue", command.getCommandValue());

            String json = objectMapper.writeValueAsString(payload);
            mqttGateway.sendToMqtt(json, mqttProperties.getTopics().getControl());
        } catch (Exception e) {
            throw new RuntimeException("MQTT控制命令下发失败：" + e.getMessage(), e);
        }
    }
}
