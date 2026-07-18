package com.xiaorong.assistant.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.config.XiaorongProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Baidu AI Cloud OCR adapter for scanned PDF resumes.
 * Credentials remain server-side and are never returned, persisted, or logged.
 */
@Component
public class BaiduOcrResumeService implements ResumeOcrService {
    private static final String TOKEN_ENDPOINT = "https://aip.baidubce.com/oauth/2.0/token";
    private static final int TOKEN_REFRESH_SKEW_SECONDS = 60;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final XiaorongProperties.Ocr properties;
    private volatile AccessToken cachedToken;

    public BaiduOcrResumeService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
                                 XiaorongProperties xiaorongProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.properties = xiaorongProperties.getInterview().getOcr();
    }

    @Override
    public boolean isAvailable() {
        return properties.isEnabled()
                && "baidu".equalsIgnoreCase(properties.getProvider())
                && StringUtils.hasText(properties.getApiKey())
                && StringUtils.hasText(properties.getSecretKey());
    }

    @Override
    public OcrResult recognizePdf(byte[] pdfBytes) {
        if (!isAvailable()) {
            throw new IllegalArgumentException("扫描版 PDF 识别未配置。请配置百度智能云 OCR，或直接粘贴简历文本。");
        }
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            int pageCount = Math.min(document.getNumberOfPages(), properties.getMaxPages());
            if (pageCount == 0) {
                throw new IllegalArgumentException("PDF 不包含可识别页面，请更换文件或直接粘贴简历文本。");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder text = new StringBuilder();
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, properties.getDpi(), ImageType.RGB);
                try {
                    String pageText = recognizeImage(toJpegBase64(image));
                    if (!pageText.isBlank()) {
                        if (!text.isEmpty()) text.append('\n');
                        text.append(pageText);
                    }
                } finally {
                    image.flush();
                }
            }
            if (text.isEmpty()) {
                throw new IllegalArgumentException("未能识别扫描版 PDF 的文字，请确认图片清晰，或直接粘贴简历文本。");
            }
            return new OcrResult(text.toString(), pageCount);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException | RestClientException ex) {
            throw new IllegalArgumentException("扫描版 PDF 识别失败，请稍后重试或直接粘贴简历文本。");
        }
    }

    private String recognizeImage(String imageBase64) throws IOException {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("image", imageBase64);
        form.add("language_type", "CHN_ENG");
        form.add("detect_direction", "true");
        String response = restClient.post()
                .uri(properties.getEndpoint() + "?access_token=" + accessToken())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
        if (root.has("error_code")) {
            throw new IllegalArgumentException("百度 OCR 暂时不可用，请稍后重试或直接粘贴简历文本。");
        }
        StringBuilder pageText = new StringBuilder();
        for (JsonNode item : root.path("words_result")) {
            String words = item.path("words").asText();
            if (StringUtils.hasText(words)) {
                if (!pageText.isEmpty()) pageText.append('\n');
                pageText.append(words);
            }
        }
        return pageText.toString();
    }

    private String accessToken() throws IOException {
        AccessToken token = cachedToken;
        if (token != null && token.validAt(Instant.now())) return token.value();
        synchronized (this) {
            token = cachedToken;
            if (token != null && token.validAt(Instant.now())) return token.value();
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", properties.getApiKey());
            form.add("client_secret", properties.getSecretKey());
            String response = restClient.post().uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String value = root.path("access_token").asText();
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException("百度 OCR 凭证无效或服务不可用，请检查服务端配置。");
            }
            long expiresIn = Math.max(root.path("expires_in").asLong(0), TOKEN_REFRESH_SKEW_SECONDS + 1L);
            cachedToken = new AccessToken(value, Instant.now().plusSeconds(expiresIn - TOKEN_REFRESH_SKEW_SECONDS));
            return value;
        }
    }

    private String toJpegBase64(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "jpg", output)) {
                throw new IOException("JPEG encoder is unavailable");
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        }
    }

    private record AccessToken(String value, Instant refreshAt) {
        boolean validAt(Instant now) {
            return refreshAt.isAfter(now);
        }
    }
}
