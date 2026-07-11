package com.agriculture.service;

import com.agriculture.dto.AiChatModelResponse;
import com.agriculture.dto.AiChatDTO;

import java.util.Optional;

public interface AiChatModelClient {

    Optional<AiChatModelResponse> chat(AiChatDTO dto, String question);
}
