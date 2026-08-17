package com.webhook.delivery.unit;

import com.webhook.delivery.service.SignatureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignatureServiceTest {

    private final SignatureService signatureService = new SignatureService();

    @Test
    @DisplayName("generateSecret produces unique 64-char hex strings")
    void testSecretGeneration() {
        String secret1 = signatureService.generateSecret();
        String secret2 = signatureService.generateSecret();

        assertNotNull(secret1);
        assertEquals(64, secret1.length(), "32-byte secret encoded as hex must be 64 characters");
        assertNotEquals(secret1, secret2, "Subsequent secrets must be unique");
    }

    @Test
    @DisplayName("computeSignature produces valid v1= prefixed HMAC-SHA256 signature")
    void testComputeSignature() {
        String secret = "test_secret_key_1234567890abcdef";
        String payload = "{\"event\":\"invoice.paid\",\"amount\":100}";
        long timestamp = 1700000000L;

        String signature = signatureService.computeSignature(payload, secret, timestamp);
        assertNotNull(signature);
        assertTrue(signature.startsWith("v1="), "Signature format must begin with 'v1='");
        assertEquals(67, signature.length(), "'v1=' (3) + 64 hex chars = 67");
    }

    @Test
    @DisplayName("verifySignature returns true for authentic payload and false for tampered payload")
    void testVerifySignatureTampering() {
        String secret = signatureService.generateSecret();
        String payload = "{\"event\":\"invoice.paid\",\"amount\":100}";
        long timestamp = 1700000000L;

        String validSignature = signatureService.computeSignature(payload, secret, timestamp);
        assertTrue(signatureService.verifySignature(payload, secret, timestamp, validSignature));

        // Tampered payload
        String tamperedPayload = "{\"event\":\"invoice.paid\",\"amount\":999}";
        assertFalse(signatureService.verifySignature(tamperedPayload, secret, timestamp, validSignature));

        // Tampered timestamp
        assertFalse(signatureService.verifySignature(payload, secret, timestamp + 1, validSignature));

        // Wrong secret
        String wrongSecret = signatureService.generateSecret();
        assertFalse(signatureService.verifySignature(payload, wrongSecret, timestamp, validSignature));
    }
}
