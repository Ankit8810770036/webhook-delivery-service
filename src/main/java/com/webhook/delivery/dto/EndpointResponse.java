package com.webhook.delivery.dto;

import com.webhook.delivery.domain.EndpointStatus;
import com.webhook.delivery.domain.WebhookEndpoint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class EndpointResponse {

    private UUID id;
    private String tenantId;
    private String url;
    private String secret;
    private List<String> subscribedEventTypes;
    private EndpointStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public EndpointResponse() {}

    public static EndpointResponse from(WebhookEndpoint endpoint) {
        EndpointResponse res = new EndpointResponse();
        res.id = endpoint.getId();
        res.tenantId = endpoint.getTenantId();
        res.url = endpoint.getUrl();
        res.secret = endpoint.getSecret();
        res.subscribedEventTypes = endpoint.getEventTypesList();
        res.status = endpoint.getStatus();
        res.createdAt = endpoint.getCreatedAt();
        res.updatedAt = endpoint.getUpdatedAt();
        return res;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getUrl() {
        return url;
    }

    public String getSecret() {
        return secret;
    }

    public List<String> getSubscribedEventTypes() {
        return subscribedEventTypes;
    }

    public EndpointStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
