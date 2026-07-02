package com.agriculture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("control_command")
public class ControlCommand {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String commandNo;

    private Long plotId;

    private Long deviceId;

    private String deviceCode;

    private String commandType;

    private String commandValue;

    private String status;

    private String requestSource;

    private String errorMessage;

    private LocalDateTime sentAt;

    private LocalDateTime ackAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
