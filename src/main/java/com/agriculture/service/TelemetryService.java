package com.agriculture.service;

import com.agriculture.entity.TelemetryData;
import com.baomidou.mybatisplus.extension.service.IService;

public interface TelemetryService extends IService<TelemetryData> {

    TelemetryData getLatestByPlotId(Long plotId);
}
