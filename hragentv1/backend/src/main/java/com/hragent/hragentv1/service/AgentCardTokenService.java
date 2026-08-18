package com.hragent.hragentv1.service;

import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AgentCardTokenService {
    private static final long TOKEN_TTL_SECONDS = 24 * 60 * 60;

    private final SecretCryptoService cryptoService;

    public AgentCardTokenService(SecretCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    public String create(
            Long tenantId,
            Long notificationId,
            Long requestId,
            Long actorUserId,
            boolean approved
    ) {
        long expiresAt = Instant.now().plusSeconds(TOKEN_TTL_SECONDS).getEpochSecond();
        String payload = String.join(
                "|",
                tenantId.toString(),
                notificationId.toString(),
                requestId.toString(),
                actorUserId.toString(),
                Boolean.toString(approved),
                Long.toString(expiresAt)
        );
        return cryptoService.encrypt(payload);
    }

    public CardAction parse(String token) {
        if (token == null || token.isBlank()) {
            throw AppException.badRequest("审批卡片令牌为空");
        }
        final String[] parts;
        try {
            parts = cryptoService.decrypt(token).split("\\|", -1);
        } catch (RuntimeException exception) {
            throw AppException.badRequest("审批卡片令牌无效");
        }
        if (parts.length != 6) {
            throw AppException.badRequest("审批卡片令牌无效");
        }
        try {
            long expiresAt = Long.parseLong(parts[5]);
            if (expiresAt < Instant.now().getEpochSecond()) {
                throw AppException.badRequest("审批卡片已过期，请在 SaaS 中处理");
            }
            return new CardAction(
                    Long.parseLong(parts[0]),
                    Long.parseLong(parts[1]),
                    Long.parseLong(parts[2]),
                    Long.parseLong(parts[3]),
                    Boolean.parseBoolean(parts[4])
            );
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AppException.badRequest("审批卡片令牌无效");
        }
    }

    public record CardAction(
            Long tenantId,
            Long notificationId,
            Long requestId,
            Long actorUserId,
            boolean approved
    ) {
    }
}
