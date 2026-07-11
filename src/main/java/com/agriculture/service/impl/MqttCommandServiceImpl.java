package com.agriculture.service.impl;

import com.agriculture.entity.ControlCommand;
import com.agriculture.mqtt.MqttGateway;
import com.agriculture.mqtt.MqttProperties;
import com.agriculture.service.CommandDispatchResult;
import com.agriculture.service.MqttCommandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttCommandServiceImpl implements MqttCommandService {

    private static final String SERVICE_ID = "Agriculture";

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
    public CommandDispatchResult sendCommand(ControlCommand command) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("commandId", command.getCommandNo());
            payload.put("commandNo", command.getCommandNo());
            payload.put("deviceCode", command.getDeviceCode());
            payload.put("commandType", command.getCommandType());
            payload.put("commandValue", resolveAction(command));
            payload.put("durationSeconds", command.getDurationSeconds());
            payload.put("brightness", command.getBrightness());
            payload.put("service_id", SERVICE_ID);
            payload.put("command_name", resolveCommandName(command.getCommandType()));
            payload.put("paras", buildParas(command));

            String json = objectMapper.writeValueAsString(payload);
            mqttGateway.sendToMqtt(json, mqttProperties.getTopics().getControl());
            return CommandDispatchResult.sent("MQTT command published to " + mqttProperties.getTopics().getControl());
        } catch (Exception e) {
            throw new RuntimeException("MQTT command publish failed: " + e.getMessage(), e);
        }
    }

    private String resolveCommandName(String commandType) {
        if ("PUMP_ON".equals(commandType) || "PUMP_OFF".equals(commandType)) {
            return "Agriculture_Control_Motor";
        }

        if ("LIGHT_ON".equals(commandType) || "LIGHT_OFF".equals(commandType)) {
            return "Agriculture_Control_Light";
        }

        throw new IllegalArgumentException("Unsupported IoTDA command type: " + commandType);
    }

    private Map<String, Object> buildParas(ControlCommand command) {
        Map<String, Object> paras = new LinkedHashMap<>();
        String action = resolveAction(command);

        if ("PUMP_ON".equals(command.getCommandType()) || "PUMP_OFF".equals(command.getCommandType())) {
            paras.put("Motor", action);
            if (command.getDurationSeconds() != null) {
                paras.put("DurationSeconds", command.getDurationSeconds());
            }
            return paras;
        }

        if ("LIGHT_ON".equals(command.getCommandType()) || "LIGHT_OFF".equals(command.getCommandType())) {
            paras.put("Light", action);
            if (command.getBrightness() != null) {
                paras.put("Brightness", command.getBrightness());
            }
            if (command.getDurationSeconds() != null) {
                paras.put("DurationSeconds", command.getDurationSeconds());
            }
            return paras;
        }

        throw new IllegalArgumentException("Unsupported IoTDA command type: " + command.getCommandType());
    }

    private String resolveAction(ControlCommand command) {
        if (command.getCommandValue() != null && !command.getCommandValue().isBlank()) {
            if ("LIGHT_ON".equals(command.getCommandType()) || "LIGHT_OFF".equals(command.getCommandType())) {
                return command.getCommandValue().trim();
            }
            return command.getCommandValue().trim().toUpperCase();
        }

        if (command.getCommandType().endsWith("_ON")) {
            return "ON";
        }

        if (command.getCommandType().endsWith("_OFF")) {
            return "OFF";
        }

        throw new IllegalArgumentException("Unsupported IoTDA command action: " + command.getCommandType());
    }
}
