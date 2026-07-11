package com.agriculture.service;

import com.agriculture.vo.AiVoiceSynthesizeVO;
import com.agriculture.vo.AiVoiceTranscribeVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

public interface AiVoiceModelClient {

    Optional<AiVoiceTranscribeVO> transcribe(MultipartFile file);

    Optional<AiVoiceSynthesizeVO> synthesize(String text, String voice, String format);
}
