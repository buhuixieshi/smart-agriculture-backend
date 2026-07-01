package com.agriculture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("telemetry_data")
public class TelemetryData {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long plotId;

    private Long deviceId;

    private BigDecimal soilMoisture;

    private BigDecimal airTemperature;

    private BigDecimal airHumidity;

    private BigDecimal illuminance;

    private String pumpStatus;

    private String lightStatus;

    private LocalDateTime collectedAt;

    private LocalDateTime createdAt;
}
