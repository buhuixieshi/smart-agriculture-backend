package com.agriculture.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WaterUsageVO {

    private Long plotId;

    private BigDecimal currentMonthUsage;

    private BigDecimal previousMonthUsage;

    private BigDecimal monthOverMonthRate;

    private BigDecimal currentYearUsage;

    private BigDecimal previousYearUsage;

    private BigDecimal yearOverYearRate;

    private BigDecimal monthlyLimit;

    private BigDecimal remainingMonthlyUsage;

    private BigDecimal monthlyUsagePercent;

    private String suggestion;
}
