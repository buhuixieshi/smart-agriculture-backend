package com.agriculture.service;

import com.agriculture.dto.AiVoiceSynthesizeDTO;
import com.agriculture.vo.AiVoiceChatVO;
import com.agriculture.vo.AiVoiceSynthesizeVO;
import com.agriculture.vo.AiVoiceTranscribeVO;
import org.springframework.web.multipart.MultipartFile;

public interface AiVoiceService {

    AiVoiceTranscribeVO transcribe(MultipartFile file);

    AiVoiceChatVO chat(MultipartFile file,
                       Long userId,
                       Long plotId,
                       String deviceCode,
                       String conversationId,
                       Boolean forceCommit,
                       Boolean synthesize);

    AiVoiceSynthesizeVO synthesize(AiVoiceSynthesizeDTO dto);
}
