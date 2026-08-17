package com.webhook.delivery.dto;

import com.webhook.delivery.domain.Delivery;
import com.webhook.delivery.domain.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public class DeliveryResponse {

    private UUID id;
    private UUID eventId;
    private UUID endpointId;
    private String tenantId;
    private DeliveryStatus status;
    private int attemptCount;
    private Instant nextAttemptAt;
    private Integer lastResponseCode;
    private String lastResponseSnippet;
    private Instant createdAt;
    private Instant updatedAt;

    public DeliveryResponse() {}

    public static DeliveryResponse from(Delivery delivery) {
        DeliveryResponse res = new DeliveryResponse();
        res.id = delivery.getId();
        res.eventId = delivery.getEventId();
        res.endpointId = delivery.getEndpointId();
        res.tenantId = delivery.getTenantId();
        res.status = delivery.getStatus();
        res.attemptCount = delivery.getAttemptCount();
        res.nextAttemptAt = delivery.getNextAttemptAt();
        res.lastResponseCode = delivery.getLastResponseCode();
        res.lastResponseSnippet = delivery.getLastResponseSnippet();
        res.createdAt = delivery.getCreatedAt();
        res.updatedAt = delivery.getUpdatedAt();
        return res;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Integer getLastResponseCode() {
        return lastResponseCode;
    }

    public String getLastResponseSnippet() {
        return lastResponseSnippet;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
