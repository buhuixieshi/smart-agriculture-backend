package com.agriculture.service;

import com.agriculture.entity.TelemetryData;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface TelemetryService extends IService<TelemetryData> {

    TelemetryData getLatestByPlotId(Long plotId);

    Page<TelemetryData> pageHistory(Long plotId, Long deviceId, Integer page, Integer size);
}
