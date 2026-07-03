package com.agriculture.controller;

import com.agriculture.common.Result;
import com.agriculture.entity.IrrigationRecord;
import com.agriculture.service.IrrigationStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/irrigation")
public class IrrigationStatsController {

    private final IrrigationStatsService irrigationStatsService;

    public IrrigationStatsController(IrrigationStatsService irrigationStatsService) {
        this.irrigationStatsService = irrigationStatsService;
    }

    @GetMapping("/list")
    public Result<List<IrrigationRecord>> list(@RequestParam(required = false) String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            return Result.fail(400, "deviceCode为必填参数");
        }

        return Result.ok(irrigationStatsService.listByDeviceCode(deviceCode));
    }
}
