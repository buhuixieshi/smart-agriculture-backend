package com.agriculture.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PestDistributionVO {

    private String pestId;

    private String pestName;

    private Long count;

    private BigDecimal percent;
}
