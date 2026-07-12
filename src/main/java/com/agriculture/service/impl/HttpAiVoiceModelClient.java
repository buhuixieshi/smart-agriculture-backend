package com.agriculture.service.impl;

import com.agriculture.config.AiVoiceModelProperties;
import com.agriculture.service.AiVoiceModelClient;
import com.agriculture.vo.AiVoiceSynthesizeVO;
import com.agriculture.vo.AiVoiceTranscribeVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class HttpAiVoiceModelClient implements AiVoiceModelClient {

    private final AiVoiceModelProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public HttpAiVoiceModelClient(AiVoiceModelProperties properties,
                                  ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutSeconds() * 1000);
        requestFactory.setReadTimeout(properties.getReadTimeoutSeconds() * 1000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public Optional<AiVoiceTranscribeVO> transcribe(MultipartFile file) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return Optional.empty();
        }

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(resolveContentType(file));
            ByteArrayResource audioResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("file", new HttpEntity<>(audioResource, fileHeaders));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(MediaType.parseMediaTypes("application/json"));
            setToken(headers);

            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    properties.getTranscribeUrl(),
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );

            AiVoiceTranscribeVO result = toTranscribeVO(response.getBody());
            if (result == null || isBlank(result.getText())) {
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("AI voice transcribe request failed, url={}, reason={}",
                    properties.getTranscribeUrl(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<AiVoiceSynthesizeVO> synthesize(String text, String voice, String format) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return Optional.empty();
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("text", text);
            if (!isBlank(voice)) {
                body.put("voice", voice);
            }
            if (!isBlank(format)) {
                body.put("format", format);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(MediaType.parseMediaTypes("application/json,audio/mpeg,audio/wav,application/octet-stream"));
            setToken(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    properties.getSynthesizeUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    byte[].class
            );

            AiVoiceSynthesizeVO result = toSynthesizeVO(text, response);
            if (result == null || (isBlank(result.getAudioBase64()) && isBlank(result.getAudioUrl()))) {
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("AI voice synthesize request failed, url={}, reason={}",
                    properties.getSynthesizeUrl(), e.getMessage());
            return Optional.empty();
        }
    }

    private AiVoiceTranscribeVO toTranscribeVO(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }

        JsonNode node = unwrap(root);
        AiVoiceTranscribeVO vo = new AiVoiceTranscribeVO();
        vo.setText(firstText(node, "text", "transcript", "transcription", "message", "content"));
        vo.setLanguage(firstText(node, "language", "lang"));
        vo.setSource(firstText(node, "source"));
        vo.setModelStatus(firstText(node, "modelStatus", "model_status", "status"));
        vo.setErrorMessage(firstText(node, "errorMessage", "error_message", "error", "msg"));
        return vo;
    }

    private AiVoiceSynthesizeVO toSynthesizeVO(String text, ResponseEntity<byte[]> response) throws IOException {
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            return null;
        }

        MediaType contentType = response.getHeaders().getContentType();
        if (contentType != null && MediaType.APPLICATION_JSON.includes(contentType)) {
            JsonNode node = unwrap(objectMapper.readTree(body));
            AiVoiceSynthesizeVO vo = new AiVoiceSynthesizeVO();
            vo.setText(firstText(node, "text", "message", "content"));
            if (isBlank(vo.getText())) {
                vo.setText(text);
            }
            vo.setAudioUrl(firstText(node, "audioUrl", "audio_url", "url"));
            vo.setAudioBase64(firstText(node, "audioBase64", "audio_base64", "audio"));
            vo.setAudioContentType(firstText(node, "audioContentType", "audio_content_type", "contentType", "content_type"));
            vo.setFormat(firstText(node, "format"));
            vo.setSource(firstText(node, "source"));
            vo.setModelStatus(firstText(node, "modelStatus", "model_status", "status"));
            vo.setErrorMessage(firstText(node, "errorMessage", "error_message", "error", "msg"));
            return vo;
        }

        AiVoiceSynthesizeVO vo = new AiVoiceSynthesizeVO();
        vo.setText(text);
        vo.setAudioBase64(Base64.getEncoder().encodeToString(body));
        vo.setAudioContentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType.toString());
        vo.setFormat(resolveFormat(contentType));
        vo.setSource("AI_VOICE_SERVICE");
        vo.setModelStatus("SUCCESS");
        return vo;
    }

    private JsonNode unwrap(JsonNode root) {
        JsonNode current = root;
        for (String name : new String[]{"data", "result", "payload"}) {
            JsonNode nested = current.get(name);
            if (nested != null && nested.isObject()) {
                current = nested;
            }
        }
        return current;
    }

    private String firstText(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull() && value.isValueNode()) {
                String text = value.asText();
                if (!isBlank(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private MediaType resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (isBlank(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return MediaType.parseMediaType(contentType);
    }

    private String resolveFormat(MediaType contentType) {
        if (contentType == null) {
            return null;
        }
        String subtype = contentType.getSubtype();
        if (subtype == null) {
            return null;
        }
        if ("mpeg".equalsIgnoreCase(subtype)) {
            return "mp3";
        }
        return subtype;
    }

    private void setToken(HttpHeaders headers) {
        String token = cleanToken(properties.getToken());
        if (token != null) {
            headers.set("X-AI-Service-Token", token);
        }
    }

    private String cleanToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String cleaned = token.trim();
        if (cleaned.contains("\r") || cleaned.contains("\n")) {
            log.warn("AI service token contains line breaks and will not be sent");
            return null;
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

}
