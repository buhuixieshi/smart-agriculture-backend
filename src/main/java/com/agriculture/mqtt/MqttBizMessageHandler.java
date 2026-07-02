package com.agriculture.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttBizMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttBizMessageHandler.class);

    private final MqttProperties mqttProperties;

    public MqttBizMessageHandler(MqttProperties mqttProperties) {
        this.mqttProperties = mqttProperties;
    }

    public void handleMessage(String topic, String payload) {
        log.info("Received MQTT message, topic={}, payload={}", topic, payload);

        if (topic == null) {
            return;
        }

        if (topic.equals(mqttProperties.getTopics().getTelemetry())) {
            handleTelemetry(payload);
        } else if (topic.equals(mqttProperties.getTopics().getHeartbeat())) {
            handleHeartbeat(payload);
        }
    }

    private void handleTelemetry(String payload) {
        log.info("Handle telemetry payload: {}", payload);
    }

    private void handleHeartbeat(String payload) {
        log.info("Handle heartbeat payload: {}", payload);
    }
}
