package com.agriculture.service.impl;

import com.agriculture.dto.AiChatDTO;
import com.agriculture.dto.AiChatModelResponse;
import com.agriculture.service.AiChatModelClient;
import com.agriculture.service.AiChatService;
import com.agriculture.vo.AiChatVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AiChatServiceImpl implements AiChatService {

    private final AiChatModelClient aiChatModelClient;

    public AiChatServiceImpl(AiChatModelClient aiChatModelClient) {
        this.aiChatModelClient = aiChatModelClient;
    }

    @Override
    public AiChatVO chat(AiChatDTO dto) {
        String question = normalizeQuestion(dto);
        if (question.isBlank()) {
            throw new IllegalArgumentException("问题内容不能为空");
        }

        Long plotId = dto == null ? null : dto.getPlotId();
        Optional<AiChatModelResponse> modelResponse = aiChatModelClient.chat(plotId, question);
        if (modelResponse.isPresent()) {
            return modelAnswer(plotId, question, modelResponse.get());
        }

        AiChatVO vo = new AiChatVO();
        vo.setPlotId(plotId);
        vo.setQuestion(question);
        vo.setAnswer(answer(question));
        vo.setSource("BACKEND_RULE_FALLBACK");
        vo.setModelStatus("MODEL_UNAVAILABLE");
        vo.setErrorMessage("未成功调用设备端文本大模型服务，请确认 ai.chat.model.url 是否可访问");
        vo.setSuggestions(List.of(
                "当前土壤湿度是否需要灌溉？",
                "如何判断补光灯是否需要打开？",
                "害虫识别结果应该怎么处理？"
        ));
        vo.setAnsweredAt(LocalDateTime.now());
        return vo;
    }

    private AiChatVO modelAnswer(Long plotId, String question, AiChatModelResponse response) {
        AiChatVO vo = new AiChatVO();
        vo.setPlotId(plotId);
        vo.setQuestion(question);
        vo.setAnswer(response.getAnswer());
        vo.setSource(response.getSource() == null || response.getSource().isBlank() ? "LLM_MODEL" : response.getSource());
        vo.setModelStatus(resolveModelStatus(response.getModelStatus()));
        vo.setSuggestions(response.getSuggestions());
        vo.setAnsweredAt(LocalDateTime.now());
        return vo;
    }

    private String resolveModelStatus(String modelStatus) {
        if (modelStatus == null || modelStatus.isBlank()) {
            return "MODEL_SERVICE";
        }
        if ("success".equalsIgnoreCase(modelStatus) || "ok".equalsIgnoreCase(modelStatus)) {
            return "MODEL_SERVICE";
        }
        return modelStatus;
    }

    private String normalizeQuestion(AiChatDTO dto) {
        if (dto == null) {
            return "";
        }
        if (dto.getMessage() != null && !dto.getMessage().isBlank()) {
            return dto.getMessage().trim();
        }
        if (dto.getQuestion() != null && !dto.getQuestion().isBlank()) {
            return dto.getQuestion().trim();
        }
        return "";
    }

    private String answer(String question) {
        String text = question.toLowerCase();

        if (containsAny(text, "灌溉", "浇水", "水泵", "pump", "irrigation")) {
            return "灌溉建议优先看土壤湿度、自动灌溉策略和最近一次水泵命令状态。当前后端自动灌溉策略只使用土壤湿度上下限、连续触发次数、最大灌溉时长和冷却时间，不再使用温度上下限。";
        }

        if (containsAny(text, "补光", "灯", "light", "照度", "光照")) {
            return "补光建议优先看照度值和补光策略。照度低于 illuminanceMin 且处于策略时间窗口内时可以开灯；照度高于 illuminanceMax 或不在时间窗口内时应关灯。真实硬件使用 BearPi 设备接收 LIGHT_ON/LIGHT_OFF 命令。";
        }

        if (containsAny(text, "虫", "害虫", "识别", "pest", "病虫害")) {
            return "智能农事问答和害虫识别统一归属于 AI 功能入口。文字问答请使用 /api/ai/chat，图片识别建议使用 /api/ai/pest/detect；旧接口 /api/pest/detect 继续保留兼容。害虫识别结果中的防治建议以后端害虫知识库为准，真实模型未连通时会返回 MOCK_FALLBACK。";
        }

        if (containsAny(text, "告警", "报警", "alarm")) {
            return "告警状态目前分为 ACTIVE、ACKNOWLEDGED、CLOSED、RECOVERED。ACTIVE 表示未处理，ACKNOWLEDGED 表示已确认，CLOSED/RECOVERED 都可以理解为已结束状态。";
        }

        if (containsAny(text, "设备", "在线", "离线", "device", "offline", "online")) {
            return "设备在线状态主要依赖最近心跳或遥测上报。真实硬件 BearPi 上报后，后端会更新设备状态、保存遥测数据，并通过 WebSocket 推送实时数据。";
        }

        return "我可以回答灌溉、补光、设备状态、告警和害虫识别相关问题。当前版本是后端规则问答服务，用于完成基础智能农事问答联调；后续如接入大模型，可保持 /api/ai/chat 接口不变，只替换后端实现。";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
