package com.agriculture.service.impl;

import com.agriculture.dto.WaterUsageLimitDTO;
import com.agriculture.entity.Alarm;
import com.agriculture.entity.Device;
import com.agriculture.entity.IrrigationRecord;
import com.agriculture.entity.WaterUsageLimit;
import com.agriculture.mapper.WaterUsageLimitMapper;
import com.agriculture.service.AlarmService;
import com.agriculture.service.DeviceService;
import com.agriculture.service.IrrigationStatsService;
import com.agriculture.service.WaterUsageLimitService;
import com.agriculture.websocket.RealtimeWebSocketHandler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class WaterUsageLimitServiceImpl extends ServiceImpl<WaterUsageLimitMapper, WaterUsageLimit>
        implements WaterUsageLimitService {

    private static final BigDecimal ESTIMATED_WATER_LITER_PER_SECOND = new BigDecimal("0.02");

    private final IrrigationStatsService irrigationStatsService;
    private final AlarmService alarmService;
    private final DeviceService deviceService;
    private final RealtimeWebSocketHandler realtimeWebSocketHandler;
    private final ObjectMapper objectMapper;

    public WaterUsageLimitServiceImpl(IrrigationStatsService irrigationStatsService,
                                      AlarmService alarmService,
                                      DeviceService deviceService,
                                      RealtimeWebSocketHandler realtimeWebSocketHandler,
                                      ObjectMapper objectMapper) {
        this.irrigationStatsService = irrigationStatsService;
        this.alarmService = alarmService;
        this.deviceService = deviceService;
        this.realtimeWebSocketHandler = realtimeWebSocketHandler;
        this.objectMapper = objectMapper;
    }

    @Override
    public WaterUsageLimit getByPlotId(Long plotId) {
        return this.getOne(
                new LambdaQueryWrapper<WaterUsageLimit>()
                        .eq(WaterUsageLimit::getPlotId, plotId)
                        .last("LIMIT 1"),
                false
        );
    }

    @Override
    @Transactional
    public WaterUsageLimit getOrCreateDefault(Long plotId) {
        WaterUsageLimit limit = getByPlotId(plotId);
        if (limit != null) {
            return limit;
        }

        LocalDateTime now = LocalDateTime.now();
        limit = new WaterUsageLimit();
        limit.setPlotId(plotId);
        limit.setDailyLimit(new BigDecimal("200.00"));
        limit.setMonthlyLimit(new BigDecimal("3000.00"));
        limit.setSingleLimit(new BigDecimal("50.00"));
        limit.setAlertPercent(new BigDecimal("80.00"));
        limit.setMinEffectiveDuration(10);
        limit.setCreatedAt(now);
        limit.setUpdatedAt(now);
        this.save(limit);
        return limit;
    }

    @Override
    @Transactional
    public WaterUsageLimit saveOrUpdateByPlotId(Long plotId, WaterUsageLimitDTO dto) {
        WaterUsageLimit limit = getByPlotId(plotId);
        LocalDateTime now = LocalDateTime.now();
        if (limit == null) {
            limit = new WaterUsageLimit();
            limit.setPlotId(plotId);
            limit.setCreatedAt(now);
        }

        if (dto.getDailyLimit() != null) {
            limit.setDailyLimit(dto.getDailyLimit());
        }
        if (dto.getMonthlyLimit() != null) {
            limit.setMonthlyLimit(dto.getMonthlyLimit());
        }
        if (dto.getSingleLimit() != null) {
            limit.setSingleLimit(dto.getSingleLimit());
        }
        if (dto.getAlertPercent() != null) {
            limit.setAlertPercent(dto.getAlertPercent());
        }
        if (dto.getMinEffectiveDuration() != null) {
            limit.setMinEffectiveDuration(dto.getMinEffectiveDuration());
        }

        fillDefaults(limit);
        limit.setUpdatedAt(now);
        this.saveOrUpdate(limit);
        return limit;
    }

    @Override
    public void checkBeforePumpOn(Device device) {
        if (device == null || device.getPlotId() == null) {
            return;
        }

        WaterUsageLimit limit;
        try {
            limit = getOrCreateDefault(device.getPlotId());
        } catch (DataAccessException e) {
            return;
        }

        BigDecimal todayUsage = sumWater(queryRecords(device.getPlotId(), LocalDate.now(), LocalDate.now()), limit);
        BigDecimal monthUsage = sumWater(
                queryRecords(device.getPlotId(), LocalDate.now().withDayOfMonth(1), LocalDate.now()),
                limit
        );

        if (limit.getDailyLimit() != null && todayUsage.compareTo(limit.getDailyLimit()) >= 0) {
            createLimitAlarm(device, "WATER_DAILY_LIMIT", todayUsage, limit.getDailyLimit(), "今日用水量已达到上限");
            throw new IllegalArgumentException("今日用水量已达到上限，禁止继续灌溉");
        }

        if (limit.getMonthlyLimit() != null && monthUsage.compareTo(limit.getMonthlyLimit()) >= 0) {
            createLimitAlarm(device, "WATER_MONTHLY_LIMIT", monthUsage, limit.getMonthlyLimit(), "本月用水量已达到上限");
            throw new IllegalArgumentException("本月用水量已达到上限，禁止继续灌溉");
        }

        BigDecimal alertPercent = limit.getAlertPercent();
        if (alertPercent != null && limit.getDailyLimit() != null && limit.getDailyLimit().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal percent = todayUsage.multiply(BigDecimal.valueOf(100))
                    .divide(limit.getDailyLimit(), 2, RoundingMode.HALF_UP);
            if (percent.compareTo(alertPercent) >= 0) {
                createLimitAlarm(device, "WATER_DAILY_WARNING", todayUsage, limit.getDailyLimit(), "今日用水量接近上限");
            }
        }
    }

    @Override
    public void checkFinishedRecord(Device device, IrrigationRecord record) {
        if (device == null || device.getPlotId() == null || record == null) {
            return;
        }

        WaterUsageLimit limit;
        try {
            limit = getOrCreateDefault(device.getPlotId());
        } catch (DataAccessException e) {
            return;
        }

        if (limit.getSingleLimit() == null || limit.getSingleLimit().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal singleUsage = resolveWaterAmount(record);
        if (singleUsage.compareTo(limit.getSingleLimit()) > 0) {
            createLimitAlarm(device, "WATER_SINGLE_LIMIT", singleUsage, limit.getSingleLimit(), "单次灌溉用水量超过上限");
        }
    }

    @Override
    public void checkAllUsageReminders() {
        List<WaterUsageLimit> limits;
        try {
            limits = this.list();
        } catch (DataAccessException e) {
            return;
        }

        for (WaterUsageLimit limit : limits) {
            try {
                checkUsageReminder(limit);
            } catch (Exception ignored) {
                // Keep scheduled reminders best-effort and never break the scheduler.
            }
        }
    }

    private void checkUsageReminder(WaterUsageLimit limit) {
        if (limit == null || limit.getPlotId() == null) {
            return;
        }

        Device device = deviceService.getOne(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getPlotId, limit.getPlotId())
                        .orderByDesc(Device::getId)
                        .last("LIMIT 1"),
                false
        );
        if (device == null) {
            return;
        }

        BigDecimal todayUsage = sumWater(queryRecords(limit.getPlotId(), LocalDate.now(), LocalDate.now()), limit);
        BigDecimal monthUsage = sumWater(
                queryRecords(limit.getPlotId(), LocalDate.now().withDayOfMonth(1), LocalDate.now()),
                limit
        );

        if (limit.getDailyLimit() != null && todayUsage.compareTo(limit.getDailyLimit()) >= 0) {
            createLimitAlarm(device, "WATER_DAILY_LIMIT", todayUsage, limit.getDailyLimit(), "今日用水量已达到上限");
        }
        if (limit.getMonthlyLimit() != null && monthUsage.compareTo(limit.getMonthlyLimit()) >= 0) {
            createLimitAlarm(device, "WATER_MONTHLY_LIMIT", monthUsage, limit.getMonthlyLimit(), "本月用水量已达到上限");
        }

        BigDecimal alertPercent = limit.getAlertPercent();
        if (alertPercent == null) {
            return;
        }
        if (limit.getDailyLimit() != null && limit.getDailyLimit().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dailyPercent = todayUsage.multiply(BigDecimal.valueOf(100))
                    .divide(limit.getDailyLimit(), 2, RoundingMode.HALF_UP);
            if (dailyPercent.compareTo(alertPercent) >= 0) {
                createLimitAlarm(device, "WATER_DAILY_WARNING", todayUsage, limit.getDailyLimit(), "今日用水量接近上限");
            }
        }
        if (limit.getMonthlyLimit() != null && limit.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal monthlyPercent = monthUsage.multiply(BigDecimal.valueOf(100))
                    .divide(limit.getMonthlyLimit(), 2, RoundingMode.HALF_UP);
            if (monthlyPercent.compareTo(alertPercent) >= 0) {
                createLimitAlarm(device, "WATER_MONTHLY_WARNING", monthUsage, limit.getMonthlyLimit(), "本月用水量接近上限");
            }
        }
    }

    private void createLimitAlarm(Device device,
                                  String alarmType,
                                  BigDecimal triggerValue,
                                  BigDecimal thresholdValue,
                                  String message) {
        boolean exists = alarmService.count(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getPlotId, device.getPlotId())
                        .eq(Alarm::getDeviceId, device.getId())
                        .eq(Alarm::getAlarmType, alarmType)
                        .eq(Alarm::getStatus, "ACTIVE")
        ) > 0;
        if (exists) {
            return;
        }

        Alarm alarm = new Alarm();
        alarm.setPlotId(device.getPlotId());
        alarm.setDeviceId(device.getId());
        alarm.setAlarmType(alarmType);
        alarm.setSeverity(alarmType.endsWith("LIMIT") ? "CRITICAL" : "WARNING");
        alarm.setTriggerValue(triggerValue);
        alarm.setThresholdValue(thresholdValue);
        alarm.setStatus("ACTIVE");
        alarm.setMessage(message);
        alarm.setCreateTime(LocalDateTime.now());
        alarmService.save(alarm);
        pushWaterReminder(device, alarm, triggerValue, thresholdValue);
    }

    private void pushWaterReminder(Device device, Alarm alarm, BigDecimal triggerValue, BigDecimal thresholdValue) {
        try {
            Map<String, Object> data = Map.of(
                    "alarmId", alarm.getId(),
                    "plotId", device.getPlotId(),
                    "deviceId", device.getId(),
                    "deviceCode", device.getDeviceCode(),
                    "alarmType", alarm.getAlarmType(),
                    "severity", alarm.getSeverity(),
                    "triggerValue", triggerValue,
                    "thresholdValue", thresholdValue,
                    "message", alarm.getMessage(),
                    "createTime", alarm.getCreateTime() == null ? "" : alarm.getCreateTime().toString()
            );
            Map<String, Object> message = Map.of(
                    "type", "WATER_USAGE_REMINDER",
                    "plotId", device.getPlotId(),
                    "data", data
            );
            realtimeWebSocketHandler.sendToAll(objectMapper.writeValueAsString(message));
        } catch (Exception ignored) {
            // WebSocket push is auxiliary; alarm persistence is the source of truth.
        }
    }

    private List<IrrigationRecord> queryRecords(Long plotId, LocalDate startDate, LocalDate endDate) {
        return irrigationStatsService.list(
                new LambdaQueryWrapper<IrrigationRecord>()
                        .eq(IrrigationRecord::getPlotId, plotId)
                        .ge(IrrigationRecord::getStartTime, startDate.atStartOfDay())
                        .lt(IrrigationRecord::getStartTime, endDate.plusDays(1).atStartOfDay())
        );
    }

    private BigDecimal sumWater(List<IrrigationRecord> records, WaterUsageLimit limit) {
        return records.stream()
                .filter(record -> resolveDurationSeconds(record) >= limit.getMinEffectiveDuration())
                .map(this::resolveWaterAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveWaterAmount(IrrigationRecord record) {
        if (record.getWaterAmount() != null) {
            return record.getWaterAmount();
        }
        return BigDecimal.valueOf(resolveDurationSeconds(record))
                .multiply(ESTIMATED_WATER_LITER_PER_SECOND)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private long resolveDurationSeconds(IrrigationRecord record) {
        if (record.getDurationSeconds() != null && record.getDurationSeconds() > 0) {
            return record.getDurationSeconds();
        }
        if (record.getStartTime() != null && record.getEndTime() != null) {
            return Math.max(0, Duration.between(record.getStartTime(), record.getEndTime()).getSeconds());
        }
        return 0;
    }

    private void fillDefaults(WaterUsageLimit limit) {
        if (limit.getDailyLimit() == null) {
            limit.setDailyLimit(new BigDecimal("200.00"));
        }
        if (limit.getMonthlyLimit() == null) {
            limit.setMonthlyLimit(new BigDecimal("3000.00"));
        }
        if (limit.getSingleLimit() == null) {
            limit.setSingleLimit(new BigDecimal("50.00"));
        }
        if (limit.getAlertPercent() == null) {
            limit.setAlertPercent(new BigDecimal("80.00"));
        }
        if (limit.getMinEffectiveDuration() == null || limit.getMinEffectiveDuration() < 0) {
            limit.setMinEffectiveDuration(10);
        }
    }
}
