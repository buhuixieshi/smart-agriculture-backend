package com.agriculture.service.impl;

import com.agriculture.config.PestModelProperties;
import com.agriculture.dto.PestModelResponse;
import com.agriculture.service.PestModelClient;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

@Service
public class HttpPestModelClient implements PestModelClient {

    private final PestModelProperties properties;
    private final RestTemplate restTemplate;

    public HttpPestModelClient(PestModelProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .build();
    }

    @Override
    public Optional<PestModelResponse> detect(Long plotId, MultipartFile file) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return Optional.empty();
        }

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new MultipartFileResource(file));
            if (plotId != null) {
                body.add("plotId", String.valueOf(plotId));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<PestModelResponse> response = restTemplate.postForEntity(
                    properties.getUrl(),
                    request,
                    PestModelResponse.class
            );

            PestModelResponse result = response.getBody();
            if (result == null || result.getPestId() == null || result.getPestId().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static class MultipartFileResource extends InputStreamResource {

        private final MultipartFile file;

        MultipartFileResource(MultipartFile file) throws IOException {
            super(file.getInputStream());
            this.file = file;
        }

        MultipartFileResource(InputStream inputStream, MultipartFile file) {
            super(inputStream);
            this.file = file;
        }

        @Override
        public String getFilename() {
            return file.getOriginalFilename();
        }

        @Override
        public long contentLength() {
            return file.getSize();
        }
    }
}
