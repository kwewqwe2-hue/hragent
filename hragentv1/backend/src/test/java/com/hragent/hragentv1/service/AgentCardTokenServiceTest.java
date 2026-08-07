package com.hragent.hragentv1.service;

import com.hragent.hragentv1.web.AppException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentCardTokenServiceTest {
    private final SecretCryptoService cryptoService =
            new SecretCryptoService("test-secret-with-more-than-16-characters");
    private final AgentCardTokenService service = new AgentCardTokenService(cryptoService);

    @Test
    void createsAndParsesAnApprovalToken() {
        String token = service.create(1L, 2L, 3L, 4L, true);

        AgentCardTokenService.CardAction action = service.parse(token);

        assertThat(action.tenantId()).isEqualTo(1L);
        assertThat(action.notificationId()).isEqualTo(2L);
        assertThat(action.requestId()).isEqualTo(3L);
        assertThat(action.actorUserId()).isEqualTo(4L);
        assertThat(action.approved()).isTrue();
    }

    @Test
    void rejectsAnInvalidToken() {
        assertThatThrownBy(() -> service.parse("not-a-valid-token"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("无效");
    }

    @Test
    void rejectsABlankToken() {
        assertThatThrownBy(() -> service.parse(" "))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("为空");
    }

    @Test
    void rejectsAnExpiredToken() {
        String payload = String.join(
                "|",
                "1",
                "2",
                "3",
                "4",
                "true",
                Long.toString(Instant.now().minusSeconds(1).getEpochSecond())
        );
        String token = cryptoService.encrypt(payload);

        assertThatThrownBy(() -> service.parse(token))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("已过期");
    }
}
