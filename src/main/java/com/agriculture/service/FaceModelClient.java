package com.agriculture.service;

import org.springframework.web.multipart.MultipartFile;

public interface FaceModelClient {

    String extractFeature(MultipartFile file);

    Double calculateDistance(MultipartFile file, String savedFeature);
}
