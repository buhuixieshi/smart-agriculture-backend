package com.agriculture.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiVoiceChatVO {

    private String transcribedText;

    private AiChatVO chat;

    private String audioUrl;

    private String audioBase64;

    private String audioContentType;

    private String voiceStatus;

    private String voiceErrorMessage;
}
