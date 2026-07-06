package com.agriculture.controller;

import com.agriculture.aspect.OperationLogRecord;
import com.agriculture.common.Result;
import com.agriculture.dto.PlotDTO;
import com.agriculture.entity.Plot;
import com.agriculture.service.PlotService;
import com.agriculture.service.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plots")
public class PlotController {

    private final PlotService plotService;
    private final TelemetryService telemetryService;

    public PlotController(PlotService plotService, TelemetryService telemetryService) {
        this.plotService = plotService;
        this.telemetryService = telemetryService;
    }

    @GetMapping
    public Result<List<Plot>> list() {
        return Result.ok(plotService.list());
    }

    @GetMapping("/{id}")
    public Result<Plot> detail(@PathVariable Long id) {
        Plot plot = plotService.getById(id);
        if (plot == null) {
            return Result.fail(400, "地块不存在：" + id);
        }
        return Result.ok(plot);
    }

    @GetMapping("/{id}/latest")
    public Result<?> latest(@PathVariable Long id) {
        return Result.ok(telemetryService.getLatestByPlotId(id));
    }

    @PostMapping
    @OperationLogRecord(type = "PLOT_CREATE", target = "plot", detail = "新增地块")
    public Result<Plot> add(@Valid @RequestBody PlotDTO dto) {
        return Result.ok(plotService.createPlot(dto));
    }

    @PutMapping("/{id}")
    @OperationLogRecord(type = "PLOT_UPDATE", target = "plot", detail = "修改地块")
    public Result<Plot> update(@PathVariable Long id, @Valid @RequestBody PlotDTO dto) {
        return Result.ok(plotService.updatePlot(id, dto));
    }

    @DeleteMapping("/{id}")
    @OperationLogRecord(type = "PLOT_DELETE", target = "plot", detail = "删除地块")
    public Result<String> delete(@PathVariable Long id) {
        plotService.deletePlot(id);
        return Result.ok("删除成功");
    }
}
