package com.agriculture.service;

import com.agriculture.dto.AiChatModelResponse;

import java.util.Optional;

public interface AiChatModelClient {

    Optional<AiChatModelResponse> chat(Long plotId, String question);
}
