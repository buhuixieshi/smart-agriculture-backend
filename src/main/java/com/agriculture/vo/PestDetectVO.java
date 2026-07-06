package com.agriculture.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PestDetectVO {

    private Long plotId;

    private String fileName;

    private String pestId;

    private String pestName;

    private String dangerLevel;

    private Double confidence;

    private String modelStatus;

    private LocalDateTime detectTime;

    private PestSuggestionVO suggestion;
}
