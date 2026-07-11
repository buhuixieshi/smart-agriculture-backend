package com.agriculture.dto;

import lombok.Data;

@Data
public class AiVoiceSynthesizeDTO {

    private String text;

    private String voice;

    private String format;
}
