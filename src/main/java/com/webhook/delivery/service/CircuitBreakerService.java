package com.webhook.delivery.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CircuitBreakerService {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerService.class);

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    public static class CircuitState {
        private State state = State.CLOSED;
        private int consecutiveFailures = 0;
        private Instant lastFailureTime;
        private Instant openUntil;

        public synchronized State evaluate(int failureThreshold, Duration cooldown) {
            Instant now = Instant.now();
            if (state == State.OPEN) {
                if (openUntil != null && now.isAfter(openUntil)) {
                    state = State.HALF_OPEN;
                    log.info("Circuit transitioned to HALF_OPEN for probe attempt");
                }
            }
            return state;
        }

        public synchronized void recordSuccess() {
            if (state != State.CLOSED) {
                log.info("Circuit recovered and transitioned to CLOSED");
            }
            this.state = State.CLOSED;
            this.consecutiveFailures = 0;
            this.openUntil = null;
        }

        public synchronized void recordFailure(int failureThreshold, Duration cooldown) {
            this.consecutiveFailures++;
            this.lastFailureTime = Instant.now();

            if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
                this.state = State.OPEN;
                this.openUntil = Instant.now().plus(cooldown);
                log.warn("Circuit TRIPPED to OPEN. Consecutive failures: {}. Cool-down until: {}",
                        consecutiveFailures, openUntil);
            }
        }

        public State getState() {
            return state;
        }

        public int getConsecutiveFailures() {
            return consecutiveFailures;
        }

        public Instant getOpenUntil() {
            return openUntil;
        }
    }

    private final int failureThreshold;
    private final Duration cooldownDuration;
    private final Map<UUID, CircuitState> circuitMap = new ConcurrentHashMap<>();

    public CircuitBreakerService(
            @Value("${webhook.circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${webhook.circuit-breaker.cooldown-seconds:60}") long cooldownSeconds
    ) {
        this.failureThreshold = failureThreshold;
        this.cooldownDuration = Duration.ofSeconds(cooldownSeconds);
    }

    public boolean allowRequest(UUID endpointId) {
        CircuitState state = circuitMap.computeIfAbsent(endpointId, k -> new CircuitState());
        State currentState = state.evaluate(failureThreshold, cooldownDuration);
        return currentState != State.OPEN;
    }

    public void recordSuccess(UUID endpointId) {
        CircuitState state = circuitMap.computeIfAbsent(endpointId, k -> new CircuitState());
        state.recordSuccess();
    }

    public void recordFailure(UUID endpointId) {
        CircuitState state = circuitMap.computeIfAbsent(endpointId, k -> new CircuitState());
        state.recordFailure(failureThreshold, cooldownDuration);
    }

    public State getState(UUID endpointId) {
        CircuitState state = circuitMap.get(endpointId);
        if (state == null) {
            return State.CLOSED;
        }
        return state.evaluate(failureThreshold, cooldownDuration);
    }

    public Duration getCooldownDuration() {
        return cooldownDuration;
    }

    public void reset(UUID endpointId) {
        circuitMap.remove(endpointId);
    }
}
