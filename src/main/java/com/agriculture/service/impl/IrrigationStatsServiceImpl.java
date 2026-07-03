package com.agriculture.service.impl;

import com.agriculture.entity.ControlCommand;
import com.agriculture.entity.Device;
import com.agriculture.entity.IrrigationRecord;
import com.agriculture.mapper.IrrigationRecordMapper;
import com.agriculture.service.IrrigationStatsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class IrrigationStatsServiceImpl extends ServiceImpl<IrrigationRecordMapper, IrrigationRecord>
        implements IrrigationStatsService {

    @Override
    public List<IrrigationRecord> listByDeviceCode(String deviceCode) {
        LambdaQueryWrapper<IrrigationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IrrigationRecord::getDeviceCode, deviceCode)
                .orderByDesc(IrrigationRecord::getStartTime);

        return this.list(wrapper);
    }

    @Override
    public IrrigationRecord startIrrigation(Device device, ControlCommand command) {
        IrrigationRecord record = new IrrigationRecord();
        record.setPlotId(device.getPlotId());
        record.setDeviceId(device.getId());
        record.setDeviceCode(device.getDeviceCode());
        record.setCommandId(command.getId());
        record.setStartTime(LocalDateTime.now());
        record.setStatus("RUNNING");

        this.save(record);
        return record;
    }

    @Override
    public IrrigationRecord finishLatestRunning(Device device, ControlCommand command) {
        LambdaQueryWrapper<IrrigationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IrrigationRecord::getDeviceCode, device.getDeviceCode())
                .eq(IrrigationRecord::getStatus, "RUNNING")
                .orderByDesc(IrrigationRecord::getStartTime)
                .last("LIMIT 1");

        IrrigationRecord record = this.getOne(wrapper, false);

        if (record == null) {
            return null;
        }

        LocalDateTime endTime = LocalDateTime.now();
        record.setEndTime(endTime);

        if (record.getStartTime() != null) {
            long seconds = Duration.between(record.getStartTime(), endTime).getSeconds();
            record.setDurationSeconds((int) seconds);
        }

        record.setStatus("FINISHED");
        this.updateById(record);

        return record;
    }
}
