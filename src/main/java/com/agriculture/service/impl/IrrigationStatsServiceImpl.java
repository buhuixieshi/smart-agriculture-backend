package com.agriculture.service.impl;

import com.agriculture.entity.ControlCommand;
import com.agriculture.entity.Device;
import com.agriculture.entity.IrrigationRecord;
import com.agriculture.mapper.ControlCommandMapper;
import com.agriculture.mapper.IrrigationRecordMapper;
import com.agriculture.service.IrrigationStatsService;
import com.agriculture.vo.DailyIrrigationTrendVO;
import com.agriculture.vo.DurationDistributionVO;
import com.agriculture.vo.IrrigationStatsVO;
import com.agriculture.vo.WaterUsageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class IrrigationStatsServiceImpl extends ServiceImpl<IrrigationRecordMapper, IrrigationRecord>
        implements IrrigationStatsService {

    private static final BigDecimal ESTIMATED_WATER_LITER_PER_SECOND = new BigDecimal("0.02");
    private static final BigDecimal DEFAULT_MONTHLY_LIMIT = new BigDecimal("3000.00");

    private final ControlCommandMapper controlCommandMapper;

    public IrrigationStatsServiceImpl(ControlCommandMapper controlCommandMapper) {
        this.controlCommandMapper = controlCommandMapper;
    }

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

    @Override
    public IrrigationStatsVO stats(Long plotId, LocalDate startDate, LocalDate endDate) {
        LocalDate[] range = resolveRange(startDate, endDate, 30);
        List<IrrigationRecord> records = queryRecords(plotId, range[0], range[1]);
        Map<Long, String> commandSources = loadCommandSources(records);

        long totalCount = records.size();
        long autoCount = records.stream().filter(record -> isAuto(record, commandSources)).count();
        long manualCount = totalCount - autoCount;
        long totalDuration = records.stream().mapToLong(this::resolveDurationSeconds).sum();
        BigDecimal totalWater = records.stream()
                .map(this::resolveWaterAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        IrrigationStatsVO vo = new IrrigationStatsVO();
        vo.setTotalCount(totalCount);
        vo.setAutoCount(autoCount);
        vo.setManualCount(manualCount);
        vo.setTotalDurationSeconds(totalDuration);
        vo.setAverageDurationSeconds(totalCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalDuration).divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP));
        vo.setTotalWaterAmount(totalWater);
        vo.setAutoRate(percent(autoCount, totalCount));
        vo.setManualRate(percent(manualCount, totalCount));
        return vo;
    }

    @Override
    public List<DailyIrrigationTrendVO> dailyTrend(Long plotId, LocalDate startDate, LocalDate endDate) {
        LocalDate[] range = resolveRange(startDate, endDate, 7);
        List<IrrigationRecord> records = queryRecords(plotId, range[0], range[1]);
        Map<Long, String> commandSources = loadCommandSources(records);
        Map<LocalDate, List<IrrigationRecord>> grouped = records.stream()
                .filter(record -> record.getStartTime() != null)
                .collect(Collectors.groupingBy(record -> record.getStartTime().toLocalDate()));

        List<DailyIrrigationTrendVO> trend = new ArrayList<>();
        LocalDate current = range[0];
        while (!current.isAfter(range[1])) {
            List<IrrigationRecord> dayRecords = grouped.getOrDefault(current, List.of());
            long autoCount = dayRecords.stream().filter(record -> isAuto(record, commandSources)).count();
            long duration = dayRecords.stream().mapToLong(this::resolveDurationSeconds).sum();
            BigDecimal water = dayRecords.stream()
                    .map(this::resolveWaterAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            DailyIrrigationTrendVO vo = new DailyIrrigationTrendVO();
            vo.setDate(current);
            vo.setTotalCount((long) dayRecords.size());
            vo.setAutoCount(autoCount);
            vo.setManualCount(dayRecords.size() - autoCount);
            vo.setDurationSeconds(duration);
            vo.setWaterAmount(water);
            trend.add(vo);
            current = current.plusDays(1);
        }
        return trend;
    }

    @Override
    public List<DurationDistributionVO> durationDistribution(Long plotId, LocalDate startDate, LocalDate endDate) {
        LocalDate[] range = resolveRange(startDate, endDate, 30);
        List<IrrigationRecord> records = queryRecords(plotId, range[0], range[1]);

        Map<String, DurationBucket> buckets = new LinkedHashMap<>();
        buckets.put("0-1分钟", new DurationBucket(0, 60));
        buckets.put("1-5分钟", new DurationBucket(61, 300));
        buckets.put("5-15分钟", new DurationBucket(301, 900));
        buckets.put("15分钟以上", new DurationBucket(901, null));

        Map<String, Long> counts = new LinkedHashMap<>();
        for (String key : buckets.keySet()) {
            counts.put(key, 0L);
        }

        for (IrrigationRecord record : records) {
            long duration = resolveDurationSeconds(record);
            for (Map.Entry<String, DurationBucket> entry : buckets.entrySet()) {
                if (entry.getValue().contains(duration)) {
                    counts.put(entry.getKey(), counts.get(entry.getKey()) + 1);
                    break;
                }
            }
        }

        List<DurationDistributionVO> result = new ArrayList<>();
        for (Map.Entry<String, DurationBucket> entry : buckets.entrySet()) {
            DurationDistributionVO vo = new DurationDistributionVO();
            vo.setName(entry.getKey());
            vo.setMinSeconds(entry.getValue().minSeconds());
            vo.setMaxSeconds(entry.getValue().maxSeconds());
            vo.setCount(counts.get(entry.getKey()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public WaterUsageVO waterUsage(Long plotId) {
        LocalDate today = LocalDate.now();
        LocalDate currentMonthStart = today.withDayOfMonth(1);
        LocalDate previousMonthStart = currentMonthStart.minusMonths(1);
        LocalDate previousMonthEnd = currentMonthStart.minusDays(1);
        LocalDate currentYearStart = today.withDayOfYear(1);
        LocalDate previousYearStart = currentYearStart.minusYears(1);
        LocalDate previousYearEnd = currentYearStart.minusDays(1);

        BigDecimal currentMonth = sumWater(queryRecords(plotId, currentMonthStart, today));
        BigDecimal previousMonth = sumWater(queryRecords(plotId, previousMonthStart, previousMonthEnd));
        BigDecimal currentYear = sumWater(queryRecords(plotId, currentYearStart, today));
        BigDecimal previousYear = sumWater(queryRecords(plotId, previousYearStart, previousYearEnd));

        WaterUsageVO vo = new WaterUsageVO();
        vo.setPlotId(plotId);
        vo.setCurrentMonthUsage(currentMonth);
        vo.setPreviousMonthUsage(previousMonth);
        vo.setMonthOverMonthRate(growthRate(currentMonth, previousMonth));
        vo.setCurrentYearUsage(currentYear);
        vo.setPreviousYearUsage(previousYear);
        vo.setYearOverYearRate(growthRate(currentYear, previousYear));
        vo.setMonthlyLimit(DEFAULT_MONTHLY_LIMIT);
        vo.setRemainingMonthlyUsage(DEFAULT_MONTHLY_LIMIT.subtract(currentMonth).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        vo.setMonthlyUsagePercent(currentMonth.multiply(BigDecimal.valueOf(100))
                .divide(DEFAULT_MONTHLY_LIMIT, 2, RoundingMode.HALF_UP));
        vo.setSuggestion(resolveWaterSuggestion(vo.getMonthlyUsagePercent()));
        return vo;
    }

    private List<IrrigationRecord> queryRecords(Long plotId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<IrrigationRecord> wrapper = new LambdaQueryWrapper<>();
        if (plotId != null) {
            wrapper.eq(IrrigationRecord::getPlotId, plotId);
        }
        if (startDate != null) {
            wrapper.ge(IrrigationRecord::getStartTime, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.lt(IrrigationRecord::getStartTime, endDate.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(IrrigationRecord::getStartTime);
        return this.list(wrapper);
    }

    private Map<Long, String> loadCommandSources(List<IrrigationRecord> records) {
        List<Long> commandIds = records.stream()
                .map(IrrigationRecord::getCommandId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> result = new HashMap<>();
        if (commandIds.isEmpty()) {
            return result;
        }

        List<ControlCommand> commands = controlCommandMapper.selectByIds(commandIds);
        for (ControlCommand command : commands) {
            result.put(command.getId(), command.getRequestSource());
        }
        return result;
    }

    private boolean isAuto(IrrigationRecord record, Map<Long, String> commandSources) {
        String source = commandSources.get(record.getCommandId());
        return "AUTO".equalsIgnoreCase(source);
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

    private BigDecimal resolveWaterAmount(IrrigationRecord record) {
        if (record.getWaterAmount() != null) {
            return record.getWaterAmount();
        }
        return BigDecimal.valueOf(resolveDurationSeconds(record))
                .multiply(ESTIMATED_WATER_LITER_PER_SECOND)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumWater(List<IrrigationRecord> records) {
        return records.stream()
                .map(this::resolveWaterAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(long part, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal growthRate(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return current != null && current.compareTo(BigDecimal.ZERO) > 0
                    ? BigDecimal.valueOf(100)
                    : BigDecimal.ZERO;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private LocalDate[] resolveRange(LocalDate startDate, LocalDate endDate, int defaultDays) {
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        LocalDate start = startDate == null ? end.minusDays(defaultDays - 1L) : startDate;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        return new LocalDate[]{start, end};
    }

    private String resolveWaterSuggestion(BigDecimal monthlyUsagePercent) {
        if (monthlyUsagePercent.compareTo(new BigDecimal("90")) >= 0) {
            return "本月用水接近上限，建议提高湿度阈值判断次数并检查是否存在漏水或频繁灌溉。";
        }
        if (monthlyUsagePercent.compareTo(new BigDecimal("70")) >= 0) {
            return "本月用水偏高，建议观察土壤湿度趋势，适当延长灌溉冷却时间。";
        }
        return "当前用水处于安全范围，可继续保持现有灌溉策略。";
    }

    private record DurationBucket(Integer minSeconds, Integer maxSeconds) {

        boolean contains(long durationSeconds) {
            return durationSeconds >= minSeconds
                    && (maxSeconds == null || durationSeconds <= maxSeconds);
        }
    }
}
