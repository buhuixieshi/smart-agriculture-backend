package com.agriculture.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiChatVO {

    private Long plotId;

    private String question;

    private String answer;

    private String source;

    private String modelStatus;

    private String errorMessage;

    private List<String> suggestions;

    private LocalDateTime answeredAt;
}
