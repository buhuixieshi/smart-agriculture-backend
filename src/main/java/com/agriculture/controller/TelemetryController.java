package com.agriculture.controller;

import com.agriculture.common.Result;
import com.agriculture.entity.TelemetryData;
import com.agriculture.service.TelemetryService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @GetMapping("/latest")
    public Result<TelemetryData> latest(@RequestParam Long plotId) {
        return Result.ok(telemetryService.getLatestByPlotId(plotId));
    }

    @GetMapping("/history")
    public Result<Page<TelemetryData>> history(@RequestParam(required = false) Long plotId,
                                               @RequestParam(required = false) Long deviceId,
                                               @RequestParam(defaultValue = "1") Integer page,
                                               @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(telemetryService.pageHistory(plotId, deviceId, page, size));
    }
}
