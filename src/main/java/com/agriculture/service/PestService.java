package com.agriculture.service;

import com.agriculture.entity.PestDetectionRecord;
import com.agriculture.vo.PestDetectVO;
import com.agriculture.vo.PestDistributionVO;
import com.agriculture.vo.PestSuggestionVO;
import com.agriculture.vo.PestTrendVO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface PestService {

    PestDetectVO detect(Long plotId, MultipartFile file);

    PestSuggestionVO getSuggestion(String pestId);

    List<PestSuggestionVO> listSuggestions();

    List<PestDetectionRecord> records(Long plotId, String pestId, LocalDate startDate, LocalDate endDate);

    List<PestTrendVO> trend(Long plotId, LocalDate startDate, LocalDate endDate);

    List<PestDistributionVO> distribution(Long plotId, LocalDate startDate, LocalDate endDate);
}
