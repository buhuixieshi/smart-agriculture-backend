package com.agriculture.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class IrrigationStrategyDTO {

    @DecimalMin(value = "0.0", message = "土壤湿度下限不能小于0")
    private BigDecimal moistureMin;

    @DecimalMin(value = "0.0", message = "土壤湿度上限不能小于0")
    private BigDecimal moistureMax;

    @Min(value = 1, message = "连续超限次数不能小于1")
    private Integer consecutiveThreshold;

    private Boolean autoMode;

    @Min(value = 1, message = "最大灌溉时长不能小于1秒")
    private Integer maxDuration;

    @Min(value = 0, message = "冷却时间不能小于0分钟")
    private Integer cooldownMinutes;
}
