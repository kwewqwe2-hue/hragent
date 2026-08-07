package com.hragent.hragentv1.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekClient {
    private final AiConfigurationService configurationService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public DeepSeekClient(AiConfigurationService configurationService, ObjectMapper objectMapper) {
        this.configurationService = configurationService;
        this.objectMapper = objectMapper;
    }

    public String providerName(Long tenantId) {
        AiConfigurationService.RuntimeConfig config = configurationService.resolve(tenantId);
        return config.configured()
                ? config.provider().toLowerCase() + ":" + config.model()
                : "mock-deepseek";
    }

    public String chat(Long tenantId, String systemPrompt, String userPrompt) throws Exception {
        AiConfigurationService.RuntimeConfig config = configurationService.resolve(tenantId);
        if (!config.configured()) {
            return mockAnswer(userPrompt);
        }
        return call(config, systemPrompt, userPrompt);
    }

    public ConnectionTest testConnection(Long tenantId) throws Exception {
        AiConfigurationService.RuntimeConfig config = configurationService.resolve(tenantId);
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalStateException("请先填写 DeepSeek API Key 并保存");
        }
        long startedAt = System.nanoTime();
        String answer = call(config, "你是连接测试助手。", "只回复 OK");
        long latencyMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        if (answer.isBlank()) {
            throw new IllegalStateException("DeepSeek 返回了空内容");
        }
        return new ConnectionTest(config.provider(), config.model(), latencyMs);
    }

    private String call(
            AiConfigurationService.RuntimeConfig config,
            String systemPrompt,
            String userPrompt
    ) throws Exception {
        Map<String, Object> payload = Map.of(
                "model", config.model(),
                "stream", false,
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl().replaceAll("/+$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(40))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = response.body() == null ? "" : response.body();
            if (body.length() > 800) {
                body = body.substring(0, 800) + "...";
            }
            throw new IllegalStateException("DeepSeek API 返回异常：" + response.statusCode() + " " + body);
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new IllegalStateException("DeepSeek API 响应中没有 message.content");
        }
        return content.asText();
    }

    private String mockAnswer(String userPrompt) {
        return """
                【模拟智能体回复】
                系统当前没有启用可用的 DeepSeek 配置，因此使用本地模拟回复。
                建议补充请假类型、开始日期、结束日期、天数和请假原因。最终审批仍以直属主管和 HR 的结果为准。
                用户问题：%s
                """.formatted(userPrompt);
    }

    public record ConnectionTest(String provider, String model, long latencyMs) {
    }
}
