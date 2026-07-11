package com.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IrrigationControlDTO {

    @NotBlank(message = "deviceCode为必填参数")
    private String deviceCode;

    @NotBlank(message = "action为必填参数")
    private String action;

    private Integer durationSeconds;
}
