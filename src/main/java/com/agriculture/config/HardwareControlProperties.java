package com.agriculture.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hardware.control")
public class HardwareControlProperties {

    private Boolean enabled = true;

    private String baseUrl = "http://192.168.20.84:3000";

    private String device = "bearpi_001";
}
