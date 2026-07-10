package com.agriculture.service.impl;

import com.agriculture.entity.TelemetryData;
import com.agriculture.mapper.TelemetryDataMapper;
import com.agriculture.service.TelemetryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class TelemetryServiceImpl extends ServiceImpl<TelemetryDataMapper, TelemetryData> implements TelemetryService {

    @Override
    public TelemetryData getLatestByPlotId(Long plotId) {
        LambdaQueryWrapper<TelemetryData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TelemetryData::getPlotId, plotId)
                .orderByDesc(TelemetryData::getCollectedAt)
                .orderByDesc(TelemetryData::getId)
                .last("LIMIT 1");

        return getOne(wrapper, false);
    }

    @Override
    public Page<TelemetryData> pageHistory(Long plotId, Long deviceId, Integer page, Integer size) {
        LambdaQueryWrapper<TelemetryData> wrapper = new LambdaQueryWrapper<>();

        if (plotId != null) {
            wrapper.eq(TelemetryData::getPlotId, plotId);
        }

        if (deviceId != null) {
            wrapper.eq(TelemetryData::getDeviceId, deviceId);
        }

        wrapper.orderByDesc(TelemetryData::getCollectedAt)
                .orderByDesc(TelemetryData::getId);

        return this.page(new Page<>(page, size), wrapper);
    }
}
