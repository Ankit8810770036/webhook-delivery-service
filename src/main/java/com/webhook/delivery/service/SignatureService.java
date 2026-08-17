package com.webhook.delivery.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class SignatureService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a cryptographically secure random 32-byte secret encoded as hex.
     */
    public String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Computes HMAC-SHA256 signature for payload with secret.
     * Signature format: v1={hex_signature}
     */
    public String computeSignature(String payload, String secret, long timestampSeconds) {
        String dataToSign = timestampSeconds + "." + (payload != null ? payload : "");
        return "v1=" + calculateHmacHex(dataToSign, secret);
    }

    /**
     * Verifies if the provided signature matches the calculated HMAC.
     */
    public boolean verifySignature(String payload, String secret, long timestampSeconds, String expectedSignature) {
        if (expectedSignature == null || !expectedSignature.startsWith("v1=")) {
            return false;
        }
        String calculated = computeSignature(payload, secret, timestampSeconds);
        return constantTimeEquals(calculated, expectedSignature);
    }

    public String calculateHmacHex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
