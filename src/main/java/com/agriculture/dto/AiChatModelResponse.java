package com.agriculture.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiChatModelResponse {

    private String answer;

    private String source;

    private String modelStatus;

    private String message;

    private List<String> suggestions;

    private Map<String, Object> actionProposal;

    private String conversationId;

    private Boolean memoryCommitted;

    private Integer memoryRecallCount;
}
