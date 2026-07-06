package com.agriculture.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DailyIrrigationTrendVO {

    private LocalDate date;

    private Long totalCount;

    private Long autoCount;

    private Long manualCount;

    private Long durationSeconds;

    private BigDecimal waterAmount;
}
