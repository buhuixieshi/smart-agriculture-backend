package com.agriculture.service;

import com.agriculture.entity.IrrigationRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IrrigationStatsService extends IService<IrrigationRecord> {

    List<IrrigationRecord> listByDeviceCode(String deviceCode);
}
