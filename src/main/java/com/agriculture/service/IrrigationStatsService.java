package com.agriculture.service;

import com.agriculture.entity.ControlCommand;
import com.agriculture.entity.Device;
import com.agriculture.entity.IrrigationRecord;
import com.agriculture.vo.DailyIrrigationTrendVO;
import com.agriculture.vo.DurationDistributionVO;
import com.agriculture.vo.IrrigationStatsVO;
import com.agriculture.vo.WaterUsageVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.util.List;

public interface IrrigationStatsService extends IService<IrrigationRecord> {

    List<IrrigationRecord> listByDeviceCode(String deviceCode);

    IrrigationRecord startIrrigation(Device device, ControlCommand command);

    IrrigationRecord finishLatestRunning(Device device, ControlCommand command);

    IrrigationStatsVO stats(Long plotId, LocalDate startDate, LocalDate endDate);

    List<DailyIrrigationTrendVO> dailyTrend(Long plotId, LocalDate startDate, LocalDate endDate);

    List<DurationDistributionVO> durationDistribution(Long plotId, LocalDate startDate, LocalDate endDate);

    WaterUsageVO waterUsage(Long plotId);
}
