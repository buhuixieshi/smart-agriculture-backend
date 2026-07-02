package com.agriculture.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

    private Boolean enabled = true;

    private String brokerUrl;

    private String clientId;

    private String username;

    private String password;

    private Integer qos = 1;

    private Topics topics = new Topics();

    @Data
    public static class Topics {

        private String telemetry;

        private String heartbeat;

        private String control;
    }
}
