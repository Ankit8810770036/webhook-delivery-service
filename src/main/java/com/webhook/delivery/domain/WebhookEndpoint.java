package com.webhook.delivery.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "endpoints")
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "url", length = 1024, nullable = false)
    private String url;

    @Column(name = "secret", length = 255, nullable = false)
    private String secret;

    @Column(name = "subscribed_event_types", columnDefinition = "TEXT", nullable = false)
    private String subscribedEventTypes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private EndpointStatus status = EndpointStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public WebhookEndpoint() {}

    public WebhookEndpoint(String tenantId, String url, String secret, List<String> eventTypes) {
        this.tenantId = tenantId;
        this.url = url;
        this.secret = secret;
        setEventTypesList(eventTypes);
        this.status = EndpointStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public List<String> getEventTypesList() {
        if (subscribedEventTypes == null || subscribedEventTypes.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(subscribedEventTypes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public void setEventTypesList(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            this.subscribedEventTypes = "*";
        } else {
            this.subscribedEventTypes = String.join(",", eventTypes);
        }
    }

    public boolean isSubscribedTo(String eventType) {
        if (status != EndpointStatus.ACTIVE) {
            return false;
        }
        List<String> types = getEventTypesList();
        return types.contains("*") || types.contains(eventType);
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getSubscribedEventTypes() {
        return subscribedEventTypes;
    }

    public void setSubscribedEventTypes(String subscribedEventTypes) {
        this.subscribedEventTypes = subscribedEventTypes;
    }

    public EndpointStatus getStatus() {
        return status;
    }

    public void setStatus(EndpointStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
