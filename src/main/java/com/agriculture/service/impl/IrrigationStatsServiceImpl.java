package com.agriculture.service.impl;

import com.agriculture.entity.IrrigationRecord;
import com.agriculture.mapper.IrrigationRecordMapper;
import com.agriculture.service.IrrigationStatsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IrrigationStatsServiceImpl extends ServiceImpl<IrrigationRecordMapper, IrrigationRecord> implements IrrigationStatsService {

    @Override
    public List<IrrigationRecord> listByDeviceCode(String deviceCode) {
        LambdaQueryWrapper<IrrigationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IrrigationRecord::getDeviceCode, deviceCode)
                .orderByDesc(IrrigationRecord::getStartTime);

        return this.list(wrapper);
    }
}
