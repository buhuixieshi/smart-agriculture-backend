package com.agriculture.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiChatModelResponse {

    private String answer;

    private String source;

    private String modelStatus;

    private String message;

    private List<String> suggestions;
}
