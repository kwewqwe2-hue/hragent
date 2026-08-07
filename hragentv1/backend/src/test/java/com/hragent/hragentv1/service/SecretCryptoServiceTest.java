package com.hragent.hragentv1.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretCryptoServiceTest {
    private final SecretCryptoService service = new SecretCryptoService("test-secret-with-more-than-16-characters");

    @Test
    void encryptsAndDecryptsWithoutStoringPlaintext() {
        String plaintext = "test-secret-value";

        String encrypted = service.encrypt(plaintext);

        assertThat(encrypted).doesNotContain(plaintext);
        assertThat(service.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void usesANewNonceForEveryEncryption() {
        String first = service.encrypt("same-value");
        String second = service.encrypt("same-value");

        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo("same-value");
        assertThat(service.decrypt(second)).isEqualTo("same-value");
    }
}
