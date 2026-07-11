package com.agriculture.service.impl;

import com.agriculture.dto.AiChatDTO;
import com.agriculture.dto.AiVoiceSynthesizeDTO;
import com.agriculture.service.AiChatService;
import com.agriculture.service.AiVoiceModelClient;
import com.agriculture.service.AiVoiceService;
import com.agriculture.vo.AiChatVO;
import com.agriculture.vo.AiVoiceChatVO;
import com.agriculture.vo.AiVoiceSynthesizeVO;
import com.agriculture.vo.AiVoiceTranscribeVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AiVoiceServiceImpl implements AiVoiceService {

    private final AiVoiceModelClient aiVoiceModelClient;
    private final AiChatService aiChatService;

    public AiVoiceServiceImpl(AiVoiceModelClient aiVoiceModelClient,
                              AiChatService aiChatService) {
        this.aiVoiceModelClient = aiVoiceModelClient;
        this.aiChatService = aiChatService;
    }

    @Override
    public AiVoiceTranscribeVO transcribe(MultipartFile file) {
        validateFile(file);
        return aiVoiceModelClient.transcribe(file)
                .orElseGet(this::voiceUnavailableTranscribe);
    }

    @Override
    public AiVoiceChatVO chat(MultipartFile file,
                              Long userId,
                              Long plotId,
                              String deviceCode,
                              String conversationId,
                              Boolean forceCommit,
                              Boolean synthesize) {
        AiVoiceChatVO vo = new AiVoiceChatVO();
        AiVoiceTranscribeVO transcribe = transcribe(file);
        vo.setTranscribedText(transcribe.getText());

        if (isBlank(transcribe.getText())) {
            vo.setVoiceStatus("ASR_UNAVAILABLE");
            vo.setVoiceErrorMessage(transcribe.getErrorMessage());
            return vo;
        }

        AiChatDTO chatDTO = new AiChatDTO();
        chatDTO.setUserId(userId);
        chatDTO.setPlotId(plotId);
        chatDTO.setConversationId(conversationId);
        chatDTO.setMessage(transcribe.getText());
        chatDTO.setForceCommit(forceCommit);
        if (!isBlank(deviceCode)) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("deviceCode", deviceCode);
            chatDTO.setContext(context);
        }

        AiChatVO chatVO = aiChatService.chat(chatDTO);
        vo.setChat(chatVO);
        vo.setVoiceStatus("SUCCESS");

        if (Boolean.TRUE.equals(synthesize) && chatVO != null && !isBlank(chatVO.getAnswer())) {
            AiVoiceSynthesizeVO audio = aiVoiceModelClient.synthesize(chatVO.getAnswer(), null, null)
                    .orElse(null);
            if (audio == null) {
                vo.setVoiceStatus("TTS_UNAVAILABLE");
                vo.setVoiceErrorMessage("语音合成服务不可用，已返回文本回答");
            } else {
                vo.setAudioUrl(audio.getAudioUrl());
                vo.setAudioBase64(audio.getAudioBase64());
                vo.setAudioContentType(audio.getAudioContentType());
            }
        }

        return vo;
    }

    @Override
    public AiVoiceSynthesizeVO synthesize(AiVoiceSynthesizeDTO dto) {
        if (dto == null || isBlank(dto.getText())) {
            throw new IllegalArgumentException("text为必填参数");
        }
        return aiVoiceModelClient.synthesize(dto.getText(), dto.getVoice(), dto.getFormat())
                .orElseGet(() -> voiceUnavailableSynthesize(dto.getText()));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("语音文件不能为空");
        }
    }

    private AiVoiceTranscribeVO voiceUnavailableTranscribe() {
        AiVoiceTranscribeVO vo = new AiVoiceTranscribeVO();
        vo.setModelStatus("MODEL_UNAVAILABLE");
        vo.setSource("BACKEND_VOICE_FALLBACK");
        vo.setErrorMessage("AI语音识别服务暂时不可用，请检查 ai.voice.model.transcribe-url 和 AI 服务进程");
        return vo;
    }

    private AiVoiceSynthesizeVO voiceUnavailableSynthesize(String text) {
        AiVoiceSynthesizeVO vo = new AiVoiceSynthesizeVO();
        vo.setText(text);
        vo.setModelStatus("MODEL_UNAVAILABLE");
        vo.setSource("BACKEND_VOICE_FALLBACK");
        vo.setErrorMessage("AI语音合成服务暂时不可用，请检查 ai.voice.model.synthesize-url 和 AI 服务进程");
        return vo;
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
