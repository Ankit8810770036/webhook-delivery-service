package com.webhook.delivery.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "events",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_tenant_event_external", columnNames = {"tenant_id", "event_id_external"})
    }
)
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "event_id_external", length = 255, nullable = false)
    private String eventIdExternal;

    @Column(name = "type", length = 128, nullable = false)
    private String type;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WebhookEvent() {}

    public WebhookEvent(String tenantId, String eventIdExternal, String type, String payload) {
        this.tenantId = tenantId;
        this.eventIdExternal = eventIdExternal;
        this.type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getEventIdExternal() {
        return eventIdExternal;
    }

    public void setEventIdExternal(String eventIdExternal) {
        this.eventIdExternal = eventIdExternal;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
