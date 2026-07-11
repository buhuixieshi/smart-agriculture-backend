package com.agriculture.dto;

import lombok.Data;

@Data
public class PestModelResponse {

    private String pestId;

    private String pestName;

    private String dangerLevel;

    private Double confidence;

    private String message;

    private String answer;

    private String source;

    private String modelStatus;
}
