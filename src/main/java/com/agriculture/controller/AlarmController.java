package com.agriculture.controller;

import com.agriculture.aspect.OperationLogRecord;
import com.agriculture.common.Result;
import com.agriculture.entity.Alarm;
import com.agriculture.service.AlarmService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @GetMapping
    public Result<List<Alarm>> list(@RequestParam(required = false) Long plotId,
                                    @RequestParam(required = false) Long deviceId,
                                    @RequestParam(required = false) String alarmType,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String severity) {
        return Result.ok(alarmService.query(plotId, deviceId, alarmType, status, severity));
    }

    @GetMapping("/{id}")
    public Result<Alarm> detail(@PathVariable Long id) {
        Alarm alarm = alarmService.getById(id);
        if (alarm == null) {
            return Result.fail(400, "告警不存在：" + id);
        }
        return Result.ok(alarm);
    }

    @PostMapping("/{id}/ack")
    @OperationLogRecord(type = "ALARM_ACK", target = "alarm", detail = "确认告警")
    public Result<Alarm> acknowledge(@PathVariable Long id) {
        return Result.ok(alarmService.acknowledge(id));
    }

    @PostMapping("/{id}/close")
    @OperationLogRecord(type = "ALARM_CLOSE", target = "alarm", detail = "关闭告警")
    public Result<Alarm> close(@PathVariable Long id) {
        return Result.ok(alarmService.close(id));
    }

    @PostMapping("/{id}/recover")
    @OperationLogRecord(type = "ALARM_RECOVER", target = "alarm", detail = "恢复告警")
    public Result<Alarm> recover(@PathVariable Long id) {
        return Result.ok(alarmService.recover(id));
    }
}
