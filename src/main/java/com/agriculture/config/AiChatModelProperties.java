package com.agriculture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.chat.model")
public class AiChatModelProperties {

    private Boolean enabled = true;

    private String url = "http://192.168.20.84:5000/api/ai/chat";

    private Integer connectTimeoutSeconds = 5;

    private Integer readTimeoutSeconds = 45;
}
