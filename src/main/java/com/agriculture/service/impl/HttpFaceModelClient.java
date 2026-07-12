package com.agriculture.service.impl;

import com.agriculture.common.BusinessException;
import com.agriculture.config.FaceModelProperties;
import com.agriculture.service.FaceModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class HttpFaceModelClient implements FaceModelClient {

    private final FaceModelProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public HttpFaceModelClient(FaceModelProperties properties,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutSeconds() * 1000);
        requestFactory.setReadTimeout(properties.getReadTimeoutSeconds() * 1000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public String extractFeature(MultipartFile file) {
        ensureEnabled();

        try {
            JsonNode root = postMultipart(properties.getUrl() + "/face/extract", file, null);

            Integer code = firstInt(root, "code");
            if (code != null && code != 200) {
                throw new BusinessException(400, firstTextOrDefault(root, "face feature extract failed",
                        "msg", "message", "error", "reason"));
            }

            JsonNode feature = firstNode(root, "feature");
            if (feature == null) {
                JsonNode data = root.path("data");
                feature = firstNode(data, "feature");
            }
            if (feature == null || feature.isNull() || feature.isMissingNode()) {
                throw new BusinessException(400, "face feature extract failed");
            }

            return objectMapper.writeValueAsString(feature);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "face model service request failed: " + e.getMessage());
        }
    }

    @Override
    public Double calculateDistance(MultipartFile file, String savedFeature) {
        ensureEnabled();

        try {
            JsonNode root = postMultipart(properties.getUrl() + "/face/distance", file, savedFeature);

            Integer code = firstInt(root, "code");
            if (code != null && code != 200) {
                throw new BusinessException(400, firstTextOrDefault(root, "face distance calculate failed",
                        "msg", "message", "error", "reason"));
            }

            Double distance = firstDouble(root, "distance");
            if (distance == null) {
                JsonNode data = root.path("data");
                distance = firstDouble(data, "distance");
            }
            if (distance == null) {
                throw new BusinessException(400, "face distance calculate failed");
            }

            return distance;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "face model service request failed: " + e.getMessage());
        }
    }

    private JsonNode postMultipart(String url, MultipartFile file, String savedFeature) throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", toResource(file));
        if (savedFeature != null) {
            body.add("feature", savedFeature);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url,
                new HttpEntity<>(body, headers),
                JsonNode.class
        );

        JsonNode root = response.getBody();
        if (root == null) {
            throw new BusinessException(500, "empty face model response");
        }
        return root;
    }

    private void ensureEnabled() {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            throw new BusinessException(500, "face model service is disabled");
        }
    }

    private ByteArrayResource toResource(MultipartFile file) throws Exception {
        return new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename() == null ? "face.jpg" : file.getOriginalFilename();
            }
        };
    }

    private JsonNode firstNode(JsonNode node, String name) {
        if (node == null) {
            return null;
        }
        return node.get(name);
    }

    private String firstText(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private String firstTextOrDefault(JsonNode node, String defaultValue, String... names) {
        String value = firstText(node, names);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Integer firstInt(JsonNode node, String name) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private Double firstDouble(JsonNode node, String name) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asDouble();
    }

}
