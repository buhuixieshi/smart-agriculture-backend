package com.agriculture.task;

import com.agriculture.service.WaterUsageLimitService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WaterUsageReminderTask {

    private final WaterUsageLimitService waterUsageLimitService;

    public WaterUsageReminderTask(WaterUsageLimitService waterUsageLimitService) {
        this.waterUsageLimitService = waterUsageLimitService;
    }

    @Scheduled(fixedDelay = 60000)
    public void checkReminders() {
        waterUsageLimitService.checkAllUsageReminders();
    }
}
