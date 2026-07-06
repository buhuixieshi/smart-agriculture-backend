package com.agriculture.service;

import com.agriculture.dto.PestModelResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

public interface PestModelClient {

    Optional<PestModelResponse> detect(Long plotId, MultipartFile file);
}
