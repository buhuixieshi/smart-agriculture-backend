package com.agriculture.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommandVO {

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
