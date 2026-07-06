package com.agriculture.vo;

import lombok.Data;

import java.util.List;

@Data
public class PestSuggestionVO {

    private String pestId;

    private String pestName;

    private String dangerLevel;

    private String description;

    private List<String> physicalControl;

    private List<String> biologicalControl;

    private List<String> chemicalControl;

    private List<String> prevention;
}
