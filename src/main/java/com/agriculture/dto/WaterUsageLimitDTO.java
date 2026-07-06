package com.agriculture.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WaterUsageLimitDTO {

    @DecimalMin(value = "0.0", message = "单日用水上限不能小于0")
    private BigDecimal dailyLimit;

    @DecimalMin(value = "0.0", message = "单月用水上限不能小于0")
    private BigDecimal monthlyLimit;

    @DecimalMin(value = "0.0", message = "单次用水上限不能小于0")
    private BigDecimal singleLimit;

    @DecimalMin(value = "0.0", message = "提醒百分比不能小于0")
    private BigDecimal alertPercent;

    @Min(value = 0, message = "最低有效灌溉时长不能小于0")
    private Integer minEffectiveDuration;
}
