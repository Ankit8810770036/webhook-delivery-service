package com.webhook.delivery.dto;

import com.webhook.delivery.domain.DeliveryStatus;
import java.time.Instant;
import java.util.UUID;

public class RedriveResponse {

    private UUID deliveryId;
    private DeliveryStatus status;
    private String message;
    private Instant reattemptAt;

    public RedriveResponse() {}

    public RedriveResponse(UUID deliveryId, DeliveryStatus status, String message, Instant reattemptAt) {
        this.deliveryId = deliveryId;
        this.status = status;
        this.message = message;
        this.reattemptAt = reattemptAt;
    }

    public UUID getDeliveryId() {
        return deliveryId;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getReattemptAt() {
        return reattemptAt;
    }
}
