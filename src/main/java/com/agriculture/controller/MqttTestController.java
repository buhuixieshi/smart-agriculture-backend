package com.agriculture.controller;

import com.agriculture.common.Result;
import com.agriculture.mqtt.MqttGateway;
import com.agriculture.mqtt.MqttProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mqtt")
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttTestController {

    private final MqttGateway mqttGateway;
    private final MqttProperties mqttProperties;

    public MqttTestController(MqttGateway mqttGateway,
                              MqttProperties mqttProperties) {
        this.mqttGateway = mqttGateway;
        this.mqttProperties = mqttProperties;
    }

    @PostMapping("/publish-test")
    public Result<String> publishTest() {
        String payload = """
                {
                  "commandNo": "TEST001",
                  "deviceCode": "DEV-A001",
                  "commandType": "PUMP_ON",
                  "commandValue": "ON"
                }
                """;

        mqttGateway.sendToMqtt(payload, mqttProperties.getTopics().getControl());

        return Result.ok("MQTT publish success");
    }
}
