package com.agriculture.task;

import com.agriculture.entity.ControlCommand;
import com.agriculture.service.ControlService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ControlCommandTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(ControlCommandTimeoutTask.class);
    private static final int COMMAND_REPLY_TIMEOUT_SECONDS = 30;

    private final ControlService controlService;

    public ControlCommandTimeoutTask(ControlService controlService) {
        this.controlService = controlService;
    }

    @Scheduled(fixedDelay = 10000)
    public void markTimeoutCommands() {
        LocalDateTime timeoutBefore = LocalDateTime.now().minusSeconds(COMMAND_REPLY_TIMEOUT_SECONDS);
        LocalDateTime now = LocalDateTime.now();

        LambdaUpdateWrapper<ControlCommand> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ControlCommand::getStatus, "SENT")
                .isNull(ControlCommand::getAckAt)
                .le(ControlCommand::getSentAt, timeoutBefore)
                .set(ControlCommand::getStatus, "TIMEOUT")
                .set(ControlCommand::getErrorMessage, "Command reply timeout after 30 seconds.")
                .set(ControlCommand::getUpdatedAt, now);

        boolean updated = controlService.update(wrapper);
        if (updated) {
            log.info("Timed out SENT control commands before {}", timeoutBefore);
        }
    }
}
