package com.agriculture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pest.model")
public class PestModelProperties {

    private Boolean enabled = false;

    private String url = "http://127.0.0.1:5002/api/ai/pest/analyze";

    private String token;

    private Integer connectTimeoutSeconds = 5;

    private Integer readTimeoutSeconds = 240;
}
