package com.agriculture.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceVO {

    private Long id;

    private String deviceCode;

    private String deviceSn;

    private String deviceName;

    private String deviceType;

    private Long plotId;

    private String status;

    private LocalDateTime lastHeartbeat;

    private Integer signalStrength;

    private Integer battery;

    private LocalDateTime registerTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Boolean telemetryEnabled;

    private Boolean pumpControllable;

    private Boolean lightControllable;

    private DeviceCapabilitiesVO capabilities;
}
