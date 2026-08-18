package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.AiProviderConfig;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.AdminDtos;
import com.hragent.hragentv1.repo.AiProviderConfigRepository;
import com.hragent.hragentv1.web.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;

@Service
public class AiConfigurationService {
    private final AiProviderConfigRepository repository;
    private final SecretCryptoService cryptoService;
    private final AuditService auditService;
    private final String environmentApiKey;
    private final String environmentBaseUrl;
    private final String environmentModel;

    public AiConfigurationService(
            AiProviderConfigRepository repository,
            SecretCryptoService cryptoService,
            AuditService auditService,
            @Value("${deepseek.api-key}") String environmentApiKey,
            @Value("${deepseek.base-url}") String environmentBaseUrl,
            @Value("${deepseek.model}") String environmentModel
    ) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.auditService = auditService;
        this.environmentApiKey = environmentApiKey;
        this.environmentBaseUrl = environmentBaseUrl;
        this.environmentModel = environmentModel;
    }

    public AdminDtos.AiConfigView view(Long tenantId) {
        return repository.findByTenantId(tenantId)
                .map(config -> toView(config, resolveApiKey(config)))
                .orElseGet(() -> new AdminDtos.AiConfigView(
                        "DEEPSEEK",
                        environmentBaseUrl,
                        environmentModel,
                        StringUtils.hasText(environmentApiKey),
                        StringUtils.hasText(environmentApiKey),
                        mask(environmentApiKey),
                        StringUtils.hasText(environmentApiKey) ? "ENVIRONMENT" : "NONE",
                        null
                ));
    }

    @Transactional
    public AdminDtos.AiConfigView update(UserAccount actor, AdminDtos.AiConfigUpdateRequest request) {
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        AiProviderConfig config = repository.findByTenantId(actor.getTenantId()).orElseGet(() -> {
            AiProviderConfig created = new AiProviderConfig();
            created.setTenantId(actor.getTenantId());
            return created;
        });

        config.setProvider("DEEPSEEK");
        config.setBaseUrl(baseUrl);
        config.setModel(request.model().trim());
        config.setEnabled(Boolean.TRUE.equals(request.enabled()));
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(actor.getId());
        if (StringUtils.hasText(request.apiKey())) {
            config.setEncryptedApiKey(cryptoService.encrypt(request.apiKey().trim()));
        }

        AiProviderConfig saved = repository.save(config);
        auditService.log(
                actor,
                "UPDATE_AI_CONFIG",
                "ai_provider_config",
                saved.getId(),
                saved.getProvider() + ":" + saved.getModel() + ", enabled=" + saved.isEnabled()
        );
        return toView(saved, resolveApiKey(saved));
    }

    public RuntimeConfig resolve(Long tenantId) {
        return repository.findByTenantId(tenantId)
                .map(config -> new RuntimeConfig(
                        config.getProvider(),
                        config.getBaseUrl(),
                        config.getModel(),
                        resolveApiKey(config),
                        config.isEnabled()
                ))
                .orElseGet(() -> new RuntimeConfig(
                        "DEEPSEEK",
                        environmentBaseUrl,
                        environmentModel,
                        environmentApiKey,
                        StringUtils.hasText(environmentApiKey)
                ));
    }

    private String resolveApiKey(AiProviderConfig config) {
        if (StringUtils.hasText(config.getEncryptedApiKey())) {
            return cryptoService.decrypt(config.getEncryptedApiKey());
        }
        return environmentApiKey;
    }

    private AdminDtos.AiConfigView toView(AiProviderConfig config, String effectiveApiKey) {
        String source = StringUtils.hasText(config.getEncryptedApiKey())
                ? "DATABASE"
                : StringUtils.hasText(environmentApiKey) ? "ENVIRONMENT" : "NONE";
        return new AdminDtos.AiConfigView(
                config.getProvider(),
                config.getBaseUrl(),
                config.getModel(),
                config.isEnabled(),
                StringUtils.hasText(effectiveApiKey),
                mask(effectiveApiKey),
                source,
                config.getUpdatedAt()
        );
    }

    private String normalizeBaseUrl(String value) {
        try {
            String normalized = value.trim().replaceAll("/+$", "");
            URI uri = URI.create(normalized);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException();
            }
            return normalized;
        } catch (Exception exception) {
            throw AppException.badRequest("Base URL 必须是有效的 http:// 或 https:// 地址");
        }
    }

    private String mask(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 3) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    public record RuntimeConfig(String provider, String baseUrl, String model, String apiKey, boolean enabled) {
        public boolean configured() {
            return enabled && StringUtils.hasText(apiKey);
        }
    }
}
