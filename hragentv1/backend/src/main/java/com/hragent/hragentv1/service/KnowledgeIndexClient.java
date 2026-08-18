package com.hragent.hragentv1.service;

import com.hragent.hragentv1.web.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class KnowledgeIndexClient {
    private final URI uploadUri;
    private final RestClient restClient;

    public KnowledgeIndexClient(@Value("${app.knowledge.n8n-upload-url}") String uploadUrl) {
        this.uploadUri = URI.create(uploadUrl);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public void uploadText(String fileName, String content) {
        ByteArrayResource resource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("data", resource)
                .filename(fileName)
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"));
        try {
            restClient.post()
                    .uri(uploadUri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new AppException(
                    HttpStatus.BAD_GATEWAY,
                    "政策正文同步到 n8n RAG 失败，候选记录仍保持待审核状态"
            );
        }
    }
}
