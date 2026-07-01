package com.agriculture.service.impl;

import com.agriculture.entity.TelemetryData;
import com.agriculture.mapper.TelemetryDataMapper;
import com.agriculture.service.TelemetryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class TelemetryServiceImpl extends ServiceImpl<TelemetryDataMapper, TelemetryData> implements TelemetryService {

    @Override
    public TelemetryData getLatestByPlotId(Long plotId) {
        LambdaQueryWrapper<TelemetryData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TelemetryData::getPlotId, plotId)
                .orderByDesc(TelemetryData::getCollectedAt)
                .last("LIMIT 1");

        return getOne(wrapper);
    }
}
