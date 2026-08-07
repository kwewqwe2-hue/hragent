package com.hragent.hragentv1.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SecretCryptoService {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCryptoService(@Value("${app.encryption-key}") String masterSecret) {
        if (masterSecret == null || masterSecret.length() < 16) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY must contain at least 16 characters");
        }
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(masterSecret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize secret encryption", exception);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce)
                    .put(ciphertext)
                    .array());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt secret", exception);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return "";
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length <= NONCE_LENGTH) {
                throw new IllegalArgumentException("Encrypted value is invalid");
            }
            byte[] nonce = new byte[NONCE_LENGTH];
            byte[] ciphertext = new byte[combined.length - NONCE_LENGTH];
            System.arraycopy(combined, 0, nonce, 0, NONCE_LENGTH);
            System.arraycopy(combined, NONCE_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt secret. Check APP_ENCRYPTION_KEY.", exception);
        }
    }
}
