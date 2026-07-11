package com.agriculture.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiChatVO {

    private String conversationId;

    private Long plotId;

    private String question;

    private String answer;

    private String status;

    private String source;

    private String modelStatus;

    private String errorMessage;

    private List<String> suggestions;

    private Map<String, Object> actionProposal;

    private Boolean memoryCommitted;

    private Integer memoryRecallCount;

    private LocalDateTime answeredAt;
}
