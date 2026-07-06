package com.agriculture.controller;

import com.agriculture.aspect.OperationLogRecord;
import com.agriculture.common.Result;
import com.agriculture.dto.WaterUsageLimitDTO;
import com.agriculture.entity.WaterUsageLimit;
import com.agriculture.service.WaterUsageLimitService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/water-limits")
public class WaterUsageLimitController {

    private final WaterUsageLimitService waterUsageLimitService;

    public WaterUsageLimitController(WaterUsageLimitService waterUsageLimitService) {
        this.waterUsageLimitService = waterUsageLimitService;
    }

    @GetMapping
    public Result<List<WaterUsageLimit>> list() {
        return Result.ok(waterUsageLimitService.list());
    }

    @GetMapping("/{plotId}")
    public Result<WaterUsageLimit> detail(@PathVariable Long plotId) {
        return Result.ok(waterUsageLimitService.getOrCreateDefault(plotId));
    }

    @PutMapping("/{plotId}")
    @OperationLogRecord(type = "WATER_LIMIT_UPDATE", target = "water_usage_limit", detail = "修改用水上限")
    public Result<WaterUsageLimit> update(@PathVariable Long plotId,
                                          @Valid @RequestBody WaterUsageLimitDTO dto) {
        return Result.ok(waterUsageLimitService.saveOrUpdateByPlotId(plotId, dto));
    }
}
