package com.agriculture.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LightStatusVO {

    private Long plotId;

    private Long deviceId;

    private String deviceCode;

    private String deviceName;

    private String deviceType;

    private Boolean lightControllable;

    private BigDecimal illuminance;

    private String lightStatus;

    private Boolean autoMode;

    private BigDecimal illuminanceMin;

    private BigDecimal illuminanceMax;

    private String latestCommandStatus;

    private LocalDateTime collectedAt;
}
