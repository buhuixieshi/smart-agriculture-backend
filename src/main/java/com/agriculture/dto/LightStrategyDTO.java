package com.agriculture.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
public class LightStrategyDTO {

    @DecimalMin(value = "0.0", message = "补光开启阈值不能小于0")
    private BigDecimal illuminanceMin;

    @DecimalMin(value = "0.0", message = "补光关闭阈值不能小于0")
    private BigDecimal illuminanceMax;

    private Boolean autoMode;

    private LocalTime startTime;

    private LocalTime endTime;

    @Min(value = 0, message = "冷却时间不能小于0")
    private Integer cooldownMinutes;
}
