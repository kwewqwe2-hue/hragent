package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.web.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class WebChatIdentityService {
    private static final String PREFIX = "web";
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] signingKey;

    public WebChatIdentityService(@Value("${app.web-chat.identity-secret}") String signingKey) {
        if (signingKey == null || signingKey.length() < 16) {
            throw new IllegalArgumentException("Web chat identity secret must contain at least 16 characters");
        }
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(UserAccount user) {
        String payload = user.getTenantId() + ":" + user.getId();
        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        return PREFIX + "." + encodedPayload + "." + encode(sign(encodedPayload));
    }

    public boolean isWebIdentity(String value) {
        return value != null && value.startsWith(PREFIX + ".");
    }

    public Identity verify(String value) {
        try {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 3 || !PREFIX.equals(parts[0])) {
                throw invalidIdentity();
            }
            byte[] expected = sign(parts[1]);
            byte[] supplied = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, supplied)) {
                throw invalidIdentity();
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String[] identifiers = payload.split(":", -1);
            if (identifiers.length != 2) {
                throw invalidIdentity();
            }
            return new Identity(Long.parseLong(identifiers[0]), Long.parseLong(identifiers[1]));
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidIdentity();
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign web chat identity", exception);
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private AppException invalidIdentity() {
        return AppException.unauthorized("Invalid web chat identity");
    }

    public record Identity(Long tenantId, Long employeeId) {
    }
}
