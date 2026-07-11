package com.agriculture.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiVoiceSynthesizeVO {

    private String text;

    private String audioUrl;

    private String audioBase64;

    private String audioContentType;

    private String format;

    private String source;

    private String modelStatus;

    private String errorMessage;
}
