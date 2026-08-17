package com.webhook.delivery.integration;

import com.webhook.delivery.service.CircuitBreakerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerIntegrationTest {

    @Test
    @DisplayName("Circuit breaker trips to OPEN after failure threshold is exceeded and recovers on success")
    void testCircuitBreakerTrippingAndRecovery() {
        int threshold = 3;
        long cooldownSeconds = 1; // fast cooldown for test
        CircuitBreakerService circuitBreaker = new CircuitBreakerService(threshold, cooldownSeconds);
        UUID endpointId = UUID.randomUUID();

        // Initially CLOSED
        assertTrue(circuitBreaker.allowRequest(endpointId));
        assertEquals(CircuitBreakerService.State.CLOSED, circuitBreaker.getState(endpointId));

        // Record 2 failures (< threshold)
        circuitBreaker.recordFailure(endpointId);
        circuitBreaker.recordFailure(endpointId);
        assertTrue(circuitBreaker.allowRequest(endpointId));
        assertEquals(CircuitBreakerService.State.CLOSED, circuitBreaker.getState(endpointId));

        // 3rd failure trips circuit to OPEN
        circuitBreaker.recordFailure(endpointId);
        assertFalse(circuitBreaker.allowRequest(endpointId));
        assertEquals(CircuitBreakerService.State.OPEN, circuitBreaker.getState(endpointId));

        // Wait for cooldown window to expire
        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // After cooldown, should be allowed (HALF_OPEN probe)
        assertTrue(circuitBreaker.allowRequest(endpointId));
        assertEquals(CircuitBreakerService.State.HALF_OPEN, circuitBreaker.getState(endpointId));

        // Successful probe resets circuit to CLOSED
        circuitBreaker.recordSuccess(endpointId);
        assertTrue(circuitBreaker.allowRequest(endpointId));
        assertEquals(CircuitBreakerService.State.CLOSED, circuitBreaker.getState(endpointId));
    }
}
