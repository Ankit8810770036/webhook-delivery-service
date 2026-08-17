package com.webhook.delivery.dto;

import com.webhook.delivery.domain.DeliveryAttempt;
import java.time.Instant;
import java.util.UUID;

public class DeliveryAttemptResponse {

    private UUID id;
    private UUID deliveryId;
    private int attemptNumber;
    private Integer responseCode;
    private Long latencyMs;
    private String errorMessage;
    private Instant createdAt;

    public DeliveryAttemptResponse() {}

    public static DeliveryAttemptResponse from(DeliveryAttempt attempt) {
        DeliveryAttemptResponse res = new DeliveryAttemptResponse();
        res.id = attempt.getId();
        res.deliveryId = attempt.getDeliveryId();
        res.attemptNumber = attempt.getAttemptNumber();
        res.responseCode = attempt.getResponseCode();
        res.latencyMs = attempt.getLatencyMs();
        res.errorMessage = attempt.getErrorMessage();
        res.createdAt = attempt.getCreatedAt();
        return res;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeliveryId() {
        return deliveryId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
