package com.agriculture.vo;

import lombok.Data;

@Data
public class DurationDistributionVO {

    private String name;

    private Integer minSeconds;

    private Integer maxSeconds;

    private Long count;
}
