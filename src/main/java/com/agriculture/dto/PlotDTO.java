package com.agriculture.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlotDTO {

    @NotBlank(message = "地块名称不能为空")
    private String name;

    private String cropType;

    private String location;

    @DecimalMin(value = "0.0", message = "地块面积不能小于0")
    private BigDecimal area;

    private String status;

    private String description;
}
