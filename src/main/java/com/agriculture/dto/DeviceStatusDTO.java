package com.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceStatusDTO {

    @NotBlank(message = "设备状态不能为空")
    private String status;
}
