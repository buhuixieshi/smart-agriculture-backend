package com.agriculture.controller;

import com.agriculture.aspect.OperationLogRecord;
import com.agriculture.common.Result;
import com.agriculture.entity.PestDetectionRecord;
import com.agriculture.service.PestService;
import com.agriculture.vo.PestDetectVO;
import com.agriculture.vo.PestDistributionVO;
import com.agriculture.vo.PestSuggestionVO;
import com.agriculture.vo.PestTrendVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pest")
public class PestController {

    private final PestService pestService;

    public PestController(PestService pestService) {
        this.pestService = pestService;
    }

    @PostMapping("/detect")
    @OperationLogRecord(type = "PEST_DETECT", target = "pest", detail = "害虫识别")
    public Result<PestDetectVO> detect(@RequestParam(required = false) Long plotId,
                                       @RequestParam("file") MultipartFile file) {
        return Result.ok(pestService.detect(plotId, file));
    }

    @GetMapping("/suggestions/{pestId}")
    public Result<PestSuggestionVO> suggestion(@PathVariable String pestId) {
        return Result.ok(pestService.getSuggestion(pestId));
    }

    @GetMapping("/suggestions")
    public Result<List<PestSuggestionVO>> suggestions() {
        return Result.ok(pestService.listSuggestions());
    }

    @GetMapping("/records")
    public Result<List<PestDetectionRecord>> records(@RequestParam(required = false) Long plotId,
                                                     @RequestParam(required = false) String pestId,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(pestService.records(plotId, pestId, startDate, endDate));
    }

    @GetMapping("/trend")
    public Result<List<PestTrendVO>> trend(@RequestParam(required = false) Long plotId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(pestService.trend(plotId, startDate, endDate));
    }

    @GetMapping("/distribution")
    public Result<List<PestDistributionVO>> distribution(@RequestParam(required = false) Long plotId,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(pestService.distribution(plotId, startDate, endDate));
    }
}
