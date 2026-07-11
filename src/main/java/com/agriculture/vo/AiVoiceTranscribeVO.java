package com.agriculture.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiVoiceTranscribeVO {

    private String text;

    private String language;

    private String source;

    private String modelStatus;

    private String errorMessage;
}
