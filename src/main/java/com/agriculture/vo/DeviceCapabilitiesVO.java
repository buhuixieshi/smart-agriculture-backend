package com.agriculture.vo;

import lombok.Data;

@Data
public class DeviceCapabilitiesVO {

    private Boolean telemetry;

    private Boolean pumpControl;

    private Boolean lightControl;

    private Boolean soilMoisture;

    private Boolean airTemperature;

    private Boolean airHumidity;

    private Boolean illuminance;
}
