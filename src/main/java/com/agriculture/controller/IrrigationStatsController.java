package com.agriculture.controller;

import com.agriculture.common.Result;
import com.agriculture.entity.IrrigationRecord;
import com.agriculture.service.IrrigationStatsService;
import com.agriculture.vo.DailyIrrigationTrendVO;
import com.agriculture.vo.DurationDistributionVO;
import com.agriculture.vo.IrrigationStatsVO;
import com.agriculture.vo.WaterUsageVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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

    @GetMapping("/stats")
    public Result<IrrigationStatsVO> stats(@RequestParam(required = false) Long plotId,
                                           @RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                           @RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(irrigationStatsService.stats(plotId, startDate, endDate));
    }

    @GetMapping("/daily-trend")
    public Result<List<DailyIrrigationTrendVO>> dailyTrend(@RequestParam(required = false) Long plotId,
                                                           @RequestParam(required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                           @RequestParam(required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(irrigationStatsService.dailyTrend(plotId, startDate, endDate));
    }

    @GetMapping("/duration-distribution")
    public Result<List<DurationDistributionVO>> durationDistribution(@RequestParam(required = false) Long plotId,
                                                                     @RequestParam(required = false)
                                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                                     @RequestParam(required = false)
                                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(irrigationStatsService.durationDistribution(plotId, startDate, endDate));
    }

    @GetMapping("/water-usage")
    public Result<WaterUsageVO> waterUsage(@RequestParam(required = false) Long plotId) {
        return Result.ok(irrigationStatsService.waterUsage(plotId));
    }
}
