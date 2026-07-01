package com.agriculture.controller;

import com.agriculture.common.Result;
import com.agriculture.service.RealtimePushService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ws")
public class WebSocketTestController {

    private final RealtimePushService realtimePushService;

    public WebSocketTestController(RealtimePushService realtimePushService) {
        this.realtimePushService = realtimePushService;
    }

    @PostMapping("/push-test")
    public Result<String> pushTest() {
        realtimePushService.pushTelemetryTest();
        return Result.ok("push success, online clients: " + realtimePushService.getOnlineCount());
    }

    @GetMapping("/online-count")
    public Result<Integer> onlineCount() {
        return Result.ok(realtimePushService.getOnlineCount());
    }
}
