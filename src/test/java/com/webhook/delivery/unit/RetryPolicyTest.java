package com.webhook.delivery.unit;

import com.webhook.delivery.service.RetryPolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    private final RetryPolicyService retryPolicy = new RetryPolicyService(
            8,        // max attempts
            5,        // initial backoff seconds
            2.0,      // multiplier
            86400,    // max backoff seconds (24h)
            0.2       // jitter factor (20%)
    );

    @Test
    @DisplayName("Attempt 0 backoff should start at initial backoff base (5s) plus jitter")
    void testAttemptZeroBackoff() {
        Duration base = retryPolicy.calculateBaseBackoff(0);
        assertEquals(5000, base.toMillis(), "Base backoff for attempt 0 must be 5000ms");

        Duration actual = retryPolicy.calculateBackoff(0);
        assertTrue(actual.toMillis() >= 5000, "Actual backoff must be >= base backoff");
        assertTrue(actual.toMillis() <= 6000, "Actual backoff must be <= 5s + 20% jitter (6000ms)");
    }

    @Test
    @DisplayName("Exponential backoff must strictly increase between successive attempts")
    void testExponentialProgression() {
        long prevBase = 0;
        for (int attempt = 0; attempt < 8; attempt++) {
            long currentBase = retryPolicy.calculateBaseBackoff(attempt).toMillis();
            assertTrue(currentBase > prevBase, "Base backoff must strictly increase at attempt " + attempt);
            prevBase = currentBase;
        }

        // Verify specific math values: 5s, 10s, 20s, 40s, 80s, 160s, 320s, 640s
        assertEquals(5000, retryPolicy.calculateBaseBackoff(0).toMillis());
        assertEquals(10000, retryPolicy.calculateBaseBackoff(1).toMillis());
        assertEquals(20000, retryPolicy.calculateBaseBackoff(2).toMillis());
        assertEquals(40000, retryPolicy.calculateBaseBackoff(3).toMillis());
        assertEquals(80000, retryPolicy.calculateBaseBackoff(4).toMillis());
        assertEquals(160000, retryPolicy.calculateBaseBackoff(5).toMillis());
        assertEquals(320000, retryPolicy.calculateBaseBackoff(6).toMillis());
        assertEquals(640000, retryPolicy.calculateBaseBackoff(7).toMillis());
    }

    @Test
    @DisplayName("Jitter bounds: actual backoff must always fall within [base, base * (1 + jitterFactor)]")
    void testJitterBounds() {
        for (int attempt = 0; attempt < 8; attempt++) {
            long baseMs = retryPolicy.calculateBaseBackoff(attempt).toMillis();
            long maxExpectedMs = (long) (baseMs * 1.25); // 20% jitter + margin

            for (int i = 0; i < 50; i++) {
                Duration backoff = retryPolicy.calculateBackoff(attempt);
                long actualMs = backoff.toMillis();
                assertTrue(actualMs >= baseMs, "Actual (" + actualMs + ") must be >= base (" + baseMs + ")");
                assertTrue(actualMs <= maxExpectedMs, "Actual (" + actualMs + ") must be <= max (" + maxExpectedMs + ")");
            }
        }
    }

    @Test
    @DisplayName("Cap at maxBackoffSeconds when attempt index is large")
    void testMaxBackoffCap() {
        Duration highAttempt = retryPolicy.calculateBaseBackoff(30);
        assertEquals(86400 * 1000L, highAttempt.toMillis(), "Must cap at 86400 seconds");
    }

    @Test
    @DisplayName("Negative attempt values default safely to attempt 0")
    void testNegativeAttemptHandling() {
        Duration negBackoff = retryPolicy.calculateBaseBackoff(-5);
        assertEquals(5000, negBackoff.toMillis());
    }

    @Test
    @DisplayName("hasExhaustedRetries should return true only after reaching maxAttempts")
    void testRetryExhaustion() {
        assertFalse(retryPolicy.hasExhaustedRetries(0));
        assertFalse(retryPolicy.hasExhaustedRetries(7));
        assertTrue(retryPolicy.hasExhaustedRetries(8));
        assertTrue(retryPolicy.hasExhaustedRetries(9));
    }

    @Test
    @DisplayName("calculateNextAttemptTime advances timestamp into the future")
    void testCalculateNextAttemptTime() {
        Instant now = Instant.now();
        Instant next = retryPolicy.calculateNextAttemptTime(0, now);
        assertTrue(next.isAfter(now));
        assertTrue(Duration.between(now, next).toSeconds() >= 5);
    }
}
