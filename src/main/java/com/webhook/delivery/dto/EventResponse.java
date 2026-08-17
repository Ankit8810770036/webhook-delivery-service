package com.webhook.delivery.dto;

import com.webhook.delivery.domain.WebhookEvent;
import java.time.Instant;
import java.util.UUID;

public class EventResponse {

    private UUID id;
    private String tenantId;
    private String eventId;
    private String type;
    private String status;
    private Instant createdAt;

    public EventResponse() {}

    public EventResponse(UUID id, String tenantId, String eventId, String type, String status, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.eventId = eventId;
        this.type = type;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static EventResponse from(WebhookEvent event, String status) {
        return new EventResponse(
                event.getId(),
                event.getTenantId(),
                event.getEventIdExternal(),
                event.getType(),
                status,
                event.getCreatedAt()
        );
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
