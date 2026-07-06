package com.agriculture.service;

import com.agriculture.entity.PestDetectionRecord;
import com.agriculture.vo.PestDetectVO;
import com.agriculture.vo.PestDistributionVO;
import com.agriculture.vo.PestTrendVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.util.List;

public interface PestDetectionRecordService extends IService<PestDetectionRecord> {

    void saveDetection(PestDetectVO detectVO);

    List<PestDetectionRecord> listRecords(Long plotId, String pestId, LocalDate startDate, LocalDate endDate);

    List<PestTrendVO> trend(Long plotId, LocalDate startDate, LocalDate endDate);

    List<PestDistributionVO> distribution(Long plotId, LocalDate startDate, LocalDate endDate);
}
