package com.agriculture.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class IrrigationStatsVO {

    private Long totalCount;

    private Long autoCount;

    private Long manualCount;

    private Long totalDurationSeconds;

    private BigDecimal averageDurationSeconds;

    private BigDecimal totalWaterAmount;

    private BigDecimal autoRate;

    private BigDecimal manualRate;
}
