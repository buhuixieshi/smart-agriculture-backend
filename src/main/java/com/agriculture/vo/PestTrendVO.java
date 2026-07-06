package com.agriculture.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
public class PestTrendVO {

    private LocalDate date;

    private Long totalCount;

    private Map<String, Long> pestCounts;
}
