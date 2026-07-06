package com.agriculture.service;

import com.agriculture.dto.WaterUsageLimitDTO;
import com.agriculture.entity.Device;
import com.agriculture.entity.IrrigationRecord;
import com.agriculture.entity.WaterUsageLimit;
import com.baomidou.mybatisplus.extension.service.IService;

public interface WaterUsageLimitService extends IService<WaterUsageLimit> {

    WaterUsageLimit getByPlotId(Long plotId);

    WaterUsageLimit getOrCreateDefault(Long plotId);

    WaterUsageLimit saveOrUpdateByPlotId(Long plotId, WaterUsageLimitDTO dto);

    void checkBeforePumpOn(Device device);

    void checkFinishedRecord(Device device, IrrigationRecord record);

    void checkAllUsageReminders();
}
