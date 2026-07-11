package com.agriculture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.voice.model")
public class AiVoiceModelProperties {

    private Boolean enabled = true;

    private String transcribeUrl = "http://127.0.0.1:5002/api/ai/voice/transcribe";

    private String synthesizeUrl = "http://127.0.0.1:5002/api/ai/voice/synthesize";

    private String token;

    private Integer connectTimeoutSeconds = 5;

    private Integer readTimeoutSeconds = 240;
}
