package com.agriculture.service.impl;

import com.agriculture.entity.PestDetectionRecord;
import com.agriculture.mapper.PestDetectionRecordMapper;
import com.agriculture.service.PestDetectionRecordService;
import com.agriculture.vo.PestDetectVO;
import com.agriculture.vo.PestDistributionVO;
import com.agriculture.vo.PestTrendVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PestDetectionRecordServiceImpl extends ServiceImpl<PestDetectionRecordMapper, PestDetectionRecord>
        implements PestDetectionRecordService {

    @Override
    public void saveDetection(PestDetectVO detectVO) {
        if (detectVO == null) {
            return;
        }

        try {
            PestDetectionRecord record = new PestDetectionRecord();
            record.setPlotId(detectVO.getPlotId());
            record.setFileName(detectVO.getFileName());
            record.setPestId(detectVO.getPestId());
            record.setPestName(detectVO.getPestName());
            record.setDangerLevel(detectVO.getDangerLevel());
            if (detectVO.getConfidence() != null) {
                record.setConfidence(BigDecimal.valueOf(detectVO.getConfidence()).setScale(4, RoundingMode.HALF_UP));
            }
            record.setModelStatus(detectVO.getModelStatus());
            record.setDetectedAt(detectVO.getDetectTime() == null ? LocalDateTime.now() : detectVO.getDetectTime());
            record.setCreatedAt(LocalDateTime.now());
            this.save(record);
        } catch (DataAccessException ignored) {
            // The detection API should still work if the optional Day8 history table has not been created.
        }
    }

    @Override
    public List<PestDetectionRecord> listRecords(Long plotId, String pestId, LocalDate startDate, LocalDate endDate) {
        try {
            return this.list(buildWrapper(plotId, pestId, startDate, endDate));
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    @Override
    public List<PestTrendVO> trend(Long plotId, LocalDate startDate, LocalDate endDate) {
        LocalDate[] range = resolveRange(startDate, endDate, 30);
        List<PestDetectionRecord> records = listRecords(plotId, null, range[0], range[1]);
        Map<LocalDate, List<PestDetectionRecord>> grouped = records.stream()
                .filter(record -> record.getDetectedAt() != null)
                .collect(Collectors.groupingBy(record -> record.getDetectedAt().toLocalDate()));

        List<PestTrendVO> result = new ArrayList<>();
        LocalDate current = range[0];
        while (!current.isAfter(range[1])) {
            List<PestDetectionRecord> dayRecords = grouped.getOrDefault(current, List.of());
            Map<String, Long> pestCounts = dayRecords.stream()
                    .collect(Collectors.groupingBy(
                            record -> record.getPestId() == null ? "unknown" : record.getPestId(),
                            LinkedHashMap::new,
                            Collectors.counting()
                    ));

            PestTrendVO vo = new PestTrendVO();
            vo.setDate(current);
            vo.setTotalCount((long) dayRecords.size());
            vo.setPestCounts(pestCounts);
            result.add(vo);
            current = current.plusDays(1);
        }
        return result;
    }

    @Override
    public List<PestDistributionVO> distribution(Long plotId, LocalDate startDate, LocalDate endDate) {
        LocalDate[] range = resolveRange(startDate, endDate, 30);
        List<PestDetectionRecord> records = listRecords(plotId, null, range[0], range[1]);
        long total = records.size();
        Map<String, List<PestDetectionRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(
                        record -> record.getPestId() == null ? "unknown" : record.getPestId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<PestDistributionVO> result = new ArrayList<>();
        for (Map.Entry<String, List<PestDetectionRecord>> entry : grouped.entrySet()) {
            PestDetectionRecord sample = entry.getValue().get(0);
            long count = entry.getValue().size();
            PestDistributionVO vo = new PestDistributionVO();
            vo.setPestId(entry.getKey());
            vo.setPestName(sample.getPestName());
            vo.setCount(count);
            vo.setPercent(total == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(count * 100).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
            result.add(vo);
        }
        return result;
    }

    private LambdaQueryWrapper<PestDetectionRecord> buildWrapper(Long plotId, String pestId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<PestDetectionRecord> wrapper = new LambdaQueryWrapper<>();
        if (plotId != null) {
            wrapper.eq(PestDetectionRecord::getPlotId, plotId);
        }
        if (pestId != null && !pestId.isBlank()) {
            wrapper.eq(PestDetectionRecord::getPestId, pestId);
        }
        if (startDate != null) {
            wrapper.ge(PestDetectionRecord::getDetectedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.lt(PestDetectionRecord::getDetectedAt, endDate.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(PestDetectionRecord::getDetectedAt);
        return wrapper;
    }

    private LocalDate[] resolveRange(LocalDate startDate, LocalDate endDate, int defaultDays) {
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        LocalDate start = startDate == null ? end.minusDays(defaultDays - 1L) : startDate;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        return new LocalDate[]{start, end};
    }
}
