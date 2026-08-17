package com.webhook.delivery.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RetryPolicyService {

    private final int maxAttempts;
    private final long initialBackoffSeconds;
    private final double backoffMultiplier;
    private final long maxBackoffSeconds;
    private final double jitterFactor;

    public RetryPolicyService(
            @Value("${webhook.delivery.max-attempts:8}") int maxAttempts,
            @Value("${webhook.delivery.initial-backoff-seconds:5}") long initialBackoffSeconds,
            @Value("${webhook.delivery.backoff-multiplier:2.0}") double backoffMultiplier,
            @Value("${webhook.delivery.max-backoff-seconds:86400}") long maxBackoffSeconds,
            @Value("${webhook.delivery.jitter-factor:0.2}") double jitterFactor
    ) {
        this.maxAttempts = maxAttempts;
        this.initialBackoffSeconds = initialBackoffSeconds;
        this.backoffMultiplier = backoffMultiplier;
        this.maxBackoffSeconds = maxBackoffSeconds;
        this.jitterFactor = jitterFactor;
    }

    /**
     * Calculates the backoff duration in seconds for a given attempt index (0-based).
     * Formula: min(initial * (multiplier ^ attempt) + uniform_jitter(0, jitterFactor * base), maxBackoff)
     *
     * Example with initial=5s, multiplier=2:
     * Attempt 0: 5s + jitter
     * Attempt 1: 10s + jitter
     * Attempt 2: 20s + jitter
     * Attempt 3: 40s + jitter
     * Attempt 4: 80s + jitter
     * Attempt 5: 160s + jitter
     * Attempt 6: 320s + jitter
     * Attempt 7: 640s + jitter
     */
    public Duration calculateBackoff(int attempt) {
        if (attempt < 0) {
            attempt = 0;
        }

        // Base exponential curve
        double baseSeconds = initialBackoffSeconds * Math.pow(backoffMultiplier, attempt);

        // Bounded jitter to prevent thundering herds while keeping progression monotonic
        double jitterRange = baseSeconds * jitterFactor;
        double jitterSeconds = ThreadLocalRandom.current().nextDouble(0, Math.max(0.001, jitterRange));

        double totalSeconds = Math.min(baseSeconds + jitterSeconds, maxBackoffSeconds);
        return Duration.ofMillis((long) (totalSeconds * 1000));
    }

    /**
     * Deterministic calculation for unit testing without random jitter.
     */
    public Duration calculateBaseBackoff(int attempt) {
        if (attempt < 0) {
            attempt = 0;
        }
        double baseSeconds = initialBackoffSeconds * Math.pow(backoffMultiplier, attempt);
        double totalSeconds = Math.min(baseSeconds, maxBackoffSeconds);
        return Duration.ofMillis((long) (totalSeconds * 1000));
    }

    public Instant calculateNextAttemptTime(int attempt, Instant now) {
        return now.plus(calculateBackoff(attempt));
    }

    public boolean hasExhaustedRetries(int attemptCount) {
        return attemptCount >= maxAttempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getInitialBackoffSeconds() {
        return initialBackoffSeconds;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public long getMaxBackoffSeconds() {
        return maxBackoffSeconds;
    }

    public double getJitterFactor() {
        return jitterFactor;
    }
}
