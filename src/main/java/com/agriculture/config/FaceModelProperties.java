package com.agriculture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "face.model")
public class FaceModelProperties {

    private Boolean enabled = true;

    private String url = "http://127.0.0.1:5001";

    private Integer connectTimeoutSeconds = 5;

    private Integer readTimeoutSeconds = 30;
}
