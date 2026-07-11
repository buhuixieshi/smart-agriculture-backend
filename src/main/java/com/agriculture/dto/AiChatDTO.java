package com.agriculture.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiChatDTO {

    private String conversationId;

    private Long userId;

    private Long plotId;

    private String message;

    private String question;

    private String query;

    private String input;

    private String prompt;

    private String text;

    private String content;

    private Map<String, Object> context;

    private Boolean forceCommit;
}
