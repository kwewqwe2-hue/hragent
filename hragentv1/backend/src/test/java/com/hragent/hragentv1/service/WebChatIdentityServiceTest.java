package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.web.AppException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebChatIdentityServiceTest {
    private final WebChatIdentityService service = new WebChatIdentityService(
            "test-web-chat-signing-secret-32-chars"
    );

    @Test
    void issuedIdentityRestoresWorkspaceAndEmployee() {
        UserAccount user = new UserAccount();
        user.setTenantId(7L);
        user.setId(23L);

        WebChatIdentityService.Identity identity = service.verify(service.issue(user));

        assertEquals(7L, identity.tenantId());
        assertEquals(23L, identity.employeeId());
    }

    @Test
    void modifiedIdentityIsRejected() {
        UserAccount user = new UserAccount();
        user.setTenantId(7L);
        user.setId(23L);
        String token = service.issue(user);

        assertThrows(AppException.class, () -> service.verify(token + "x"));
    }
}
