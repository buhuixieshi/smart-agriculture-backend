package com.agriculture.service.impl;

import com.agriculture.entity.Alarm;
import com.agriculture.mapper.AlarmMapper;
import com.agriculture.service.AlarmService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlarmServiceImpl extends ServiceImpl<AlarmMapper, Alarm> implements AlarmService {

    @Override
    public List<Alarm> query(Long plotId, Long deviceId, String alarmType, String status, String severity) {
        LambdaQueryWrapper<Alarm> wrapper = new LambdaQueryWrapper<>();

        if (plotId != null) {
            wrapper.eq(Alarm::getPlotId, plotId);
        }
        if (deviceId != null) {
            wrapper.eq(Alarm::getDeviceId, deviceId);
        }
        if (alarmType != null && !alarmType.isBlank()) {
            wrapper.eq(Alarm::getAlarmType, alarmType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(Alarm::getStatus, status);
        }
        if (severity != null && !severity.isBlank()) {
            wrapper.eq(Alarm::getSeverity, severity);
        }

        wrapper.orderByDesc(Alarm::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    @Transactional
    public Alarm acknowledge(Long id) {
        Alarm alarm = requireAlarm(id);
        if (!"ACTIVE".equals(alarm.getStatus())) {
            throw new IllegalArgumentException("只有ACTIVE告警可以确认");
        }
        alarm.setStatus("ACKNOWLEDGED");
        this.updateById(alarm);
        return alarm;
    }

    @Override
    @Transactional
    public Alarm close(Long id) {
        Alarm alarm = requireAlarm(id);
        if ("RECOVERED".equals(alarm.getStatus()) || "CLOSED".equals(alarm.getStatus())) {
            return alarm;
        }
        alarm.setStatus("CLOSED");
        alarm.setResolveTime(LocalDateTime.now());
        this.updateById(alarm);
        return alarm;
    }

    @Override
    @Transactional
    public Alarm recover(Long id) {
        Alarm alarm = requireAlarm(id);
        if ("CLOSED".equals(alarm.getStatus()) || "RECOVERED".equals(alarm.getStatus())) {
            return alarm;
        }
        alarm.setStatus("RECOVERED");
        alarm.setResolveTime(LocalDateTime.now());
        this.updateById(alarm);
        return alarm;
    }

    @Override
    @Transactional
    public void recoverActiveDeviceOffline(Long deviceId) {
        if (deviceId == null) {
            return;
        }

        List<Alarm> alarms = this.list(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getDeviceId, deviceId)
                        .eq(Alarm::getAlarmType, "DEVICE_OFFLINE")
                        .in(Alarm::getStatus, "ACTIVE", "ACKED", "ACKNOWLEDGED")
        );

        LocalDateTime now = LocalDateTime.now();
        for (Alarm alarm : alarms) {
            alarm.setStatus("RECOVERED");
            alarm.setResolveTime(now);
            this.updateById(alarm);
        }
    }

    private Alarm requireAlarm(Long id) {
        Alarm alarm = this.getById(id);
        if (alarm == null) {
            throw new IllegalArgumentException("告警不存在：" + id);
        }
        return alarm;
    }
}
