package com.agriculture.service.impl;

import com.agriculture.dto.PestModelResponse;
import com.agriculture.entity.PestKnowledge;
import com.agriculture.entity.PestDetectionRecord;
import com.agriculture.service.PestDetectionRecordService;
import com.agriculture.service.PestKnowledgeService;
import com.agriculture.service.PestModelClient;
import com.agriculture.service.PestService;
import com.agriculture.vo.PestDetectVO;
import com.agriculture.vo.PestDistributionVO;
import com.agriculture.vo.PestSuggestionVO;
import com.agriculture.vo.PestTrendVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PestServiceImpl implements PestService {

    private final PestKnowledgeService pestKnowledgeService;
    private final PestModelClient pestModelClient;
    private final PestDetectionRecordService pestDetectionRecordService;
    private final Map<String, PestSuggestionVO> fallbackKnowledgeBase = buildFallbackKnowledgeBase();

    public PestServiceImpl(PestKnowledgeService pestKnowledgeService,
                           PestModelClient pestModelClient,
                           PestDetectionRecordService pestDetectionRecordService) {
        this.pestKnowledgeService = pestKnowledgeService;
        this.pestModelClient = pestModelClient;
        this.pestDetectionRecordService = pestDetectionRecordService;
    }

    @Override
    public PestDetectVO detect(Long plotId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("图片文件不能为空");
        }

        String fileName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        Optional<PestModelResponse> modelResponse = pestModelClient.detect(plotId, file);
        PestSuggestionVO suggestion = modelResponse
                .map(PestModelResponse::getPestId)
                .map(this::findSuggestionOrFallback)
                .orElseGet(() -> findSuggestionOrFallback(resolveMockPestId(fileName)));

        PestDetectVO result = new PestDetectVO();
        result.setPlotId(plotId);
        result.setFileName(fileName);
        result.setPestId(suggestion.getPestId());
        result.setPestName(resolvePestName(modelResponse, suggestion));
        result.setDangerLevel(resolveDangerLevel(modelResponse, suggestion));
        result.setConfidence(modelResponse.map(PestModelResponse::getConfidence)
                .orElseGet(() -> resolveMockConfidence(fileName, file.getSize())));
        result.setModelStatus(modelResponse.isPresent() ? "MODEL_SERVICE" : "MOCK_FALLBACK");
        result.setDetectTime(LocalDateTime.now());
        result.setSuggestion(suggestion);
        pestDetectionRecordService.saveDetection(result);
        return result;
    }

    @Override
    public PestSuggestionVO getSuggestion(String pestId) {
        if (pestId == null || pestId.isBlank()) {
            throw new IllegalArgumentException("pestId为必填参数");
        }

        PestSuggestionVO suggestion = findSuggestionOrFallback(pestId);
        if (suggestion == null) {
            throw new IllegalArgumentException("害虫知识不存在：" + pestId);
        }
        return suggestion;
    }

    @Override
    public List<PestSuggestionVO> listSuggestions() {
        try {
            List<PestKnowledge> knowledgeList = pestKnowledgeService.listAll();
            if (!knowledgeList.isEmpty()) {
                return knowledgeList.stream().map(this::toSuggestionVO).toList();
            }
        } catch (Exception ignored) {
            // If the optional Day7 BE-B table has not been created yet, keep the API usable.
        }
        return new ArrayList<>(fallbackKnowledgeBase.values());
    }

    @Override
    public List<PestDetectionRecord> records(Long plotId, String pestId, LocalDate startDate, LocalDate endDate) {
        return pestDetectionRecordService.listRecords(plotId, pestId, startDate, endDate);
    }

    @Override
    public List<PestTrendVO> trend(Long plotId, LocalDate startDate, LocalDate endDate) {
        return pestDetectionRecordService.trend(plotId, startDate, endDate);
    }

    @Override
    public List<PestDistributionVO> distribution(Long plotId, LocalDate startDate, LocalDate endDate) {
        return pestDetectionRecordService.distribution(plotId, startDate, endDate);
    }

    private PestSuggestionVO findSuggestionOrFallback(String pestId) {
        try {
            PestKnowledge knowledge = pestKnowledgeService.getByPestId(pestId);
            if (knowledge != null) {
                return toSuggestionVO(knowledge);
            }
        } catch (Exception ignored) {
            // Fallback is used while the database knowledge table is not ready.
        }
        return fallbackKnowledgeBase.get(pestId);
    }

    private PestSuggestionVO toSuggestionVO(PestKnowledge knowledge) {
        PestSuggestionVO suggestion = new PestSuggestionVO();
        suggestion.setPestId(knowledge.getPestId());
        suggestion.setPestName(knowledge.getPestName());
        suggestion.setDangerLevel(knowledge.getDangerLevel());
        suggestion.setDescription(knowledge.getDescription());
        suggestion.setPhysicalControl(splitLines(knowledge.getPhysicalControl()));
        suggestion.setBiologicalControl(splitLines(knowledge.getBiologicalControl()));
        suggestion.setChemicalControl(splitLines(knowledge.getChemicalControl()));
        suggestion.setPrevention(splitLines(knowledge.getPrevention()));
        return suggestion;
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\\\\n|\\r?\\n"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private String resolvePestName(Optional<PestModelResponse> modelResponse, PestSuggestionVO suggestion) {
        if (suggestion.getPestName() != null && !suggestion.getPestName().isBlank()) {
            return suggestion.getPestName();
        }
        return modelResponse.map(PestModelResponse::getPestName)
                .filter(name -> !name.isBlank())
                .orElse("未知害虫");
    }

    private String resolveDangerLevel(Optional<PestModelResponse> modelResponse, PestSuggestionVO suggestion) {
        if (suggestion.getDangerLevel() != null && !suggestion.getDangerLevel().isBlank()) {
            return suggestion.getDangerLevel();
        }
        return modelResponse.map(PestModelResponse::getDangerLevel)
                .filter(level -> !level.isBlank())
                .orElse("LOW");
    }

    private String resolveMockPestId(String fileName) {
        List<String> pestIds = new ArrayList<>(fallbackKnowledgeBase.keySet());
        int index = Math.abs(fileName.hashCode()) % pestIds.size();
        return pestIds.get(index);
    }

    private Double resolveMockConfidence(String fileName, long size) {
        int seed = Math.abs((fileName + size).hashCode());
        return 0.72 + (seed % 24) / 100.0;
    }

    private Map<String, PestSuggestionVO> buildFallbackKnowledgeBase() {
        Map<String, PestSuggestionVO> map = new LinkedHashMap<>();
        put(map, "aphid", "蚜虫", "MEDIUM",
                "常聚集在嫩叶和新梢吸食汁液，容易诱发叶片卷曲和病毒病传播。",
                List.of("剪除虫量集中的嫩梢", "悬挂黄色粘虫板诱捕有翅蚜"),
                List.of("保护瓢虫、草蛉等天敌", "释放蚜茧蜂进行生物控制"),
                List.of("虫口密度较高时选用吡虫啉或啶虫脒，按说明低浓度喷施"),
                List.of("避免氮肥过量", "加强通风，降低植株郁闭度"));
        put(map, "whitefly", "白粉虱", "MEDIUM",
                "成虫和若虫在叶背吸汁，分泌蜜露并诱发煤污病。",
                List.of("悬挂黄色粘虫板", "及时清除老叶和病残体"),
                List.of("释放丽蚜小蜂", "保护捕食性天敌"),
                List.of("可选用噻虫嗪或螺虫乙酯轮换防治"),
                List.of("定植前清洁棚室", "控制温湿度，减少虫源积累"));
        put(map, "thrips", "蓟马", "MEDIUM",
                "危害花器和嫩叶，造成银白色斑纹、畸形和落花。",
                List.of("使用蓝色粘虫板监测诱捕", "摘除受害严重花叶"),
                List.of("释放小花蝽等天敌", "保护捕食螨"),
                List.of("可选用乙基多杀菌素或虫螨腈，注意轮换用药"),
                List.of("清除杂草寄主", "花期加强巡查"));
        put(map, "spider_mite", "红蜘蛛", "HIGH",
                "高温干旱条件下暴发快，叶片出现失绿斑点和蛛网。",
                List.of("增加叶面湿度", "摘除虫量高的老叶"),
                List.of("释放捕食螨", "保护草蛉等天敌"),
                List.of("可选用阿维菌素、乙螨唑等药剂轮换防治"),
                List.of("避免长期干旱", "定期检查叶背"));
        put(map, "armyworm", "粘虫", "HIGH",
                "幼虫啃食叶片，虫龄增大后食量明显增加。",
                List.of("人工摘除卵块和低龄幼虫", "安装诱虫灯监测"),
                List.of("使用苏云金杆菌制剂防治低龄幼虫"),
                List.of("高虫量时可选用甲维盐类药剂"),
                List.of("加强田间巡查", "集中处理杂草和残株"));
        return map;
    }

    private void put(Map<String, PestSuggestionVO> map,
                     String pestId,
                     String pestName,
                     String dangerLevel,
                     String description,
                     List<String> physicalControl,
                     List<String> biologicalControl,
                     List<String> chemicalControl,
                     List<String> prevention) {
        PestSuggestionVO suggestion = new PestSuggestionVO();
        suggestion.setPestId(pestId);
        suggestion.setPestName(pestName);
        suggestion.setDangerLevel(dangerLevel);
        suggestion.setDescription(description);
        suggestion.setPhysicalControl(physicalControl);
        suggestion.setBiologicalControl(biologicalControl);
        suggestion.setChemicalControl(chemicalControl);
        suggestion.setPrevention(prevention);
        map.put(pestId, suggestion);
    }
}
