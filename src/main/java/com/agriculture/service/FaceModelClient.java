package com.agriculture.service;

import org.springframework.web.multipart.MultipartFile;

public interface FaceModelClient {

    String extractFeature(MultipartFile file);

    boolean compareFace(MultipartFile file, String savedFeature);
}
