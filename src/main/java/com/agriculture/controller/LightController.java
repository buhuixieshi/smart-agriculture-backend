package com.agriculture.controller;

import com.agriculture.aspect.OperationLogRecord;
import com.agriculture.common.Result;
import com.agriculture.dto.LightControlDTO;
import com.agriculture.service.LightControlService;
import com.agriculture.vo.CommandVO;
import com.agriculture.vo.LightStatusVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/light")
public class LightController {

    private final LightControlService lightControlService;

    public LightController(LightControlService lightControlService) {
        this.lightControlService = lightControlService;
    }

    @PostMapping("/control")
    @OperationLogRecord(type = "LIGHT_CONTROL", target = "light", detail = "补光灯控制")
    public Result<CommandVO> control(@Valid @RequestBody LightControlDTO dto) {
        return Result.ok(lightControlService.control(dto));
    }

    @GetMapping("/status")
    public Result<LightStatusVO> status(@RequestParam(required = false) Long plotId,
                                        @RequestParam(required = false) String deviceCode) {
        if (plotId == null && (deviceCode == null || deviceCode.isBlank())) {
            return Result.fail(400, "plotId或deviceCode至少传一个");
        }
        return Result.ok(lightControlService.getStatus(plotId, deviceCode));
    }
}
