package com.webhook.delivery.dto;

import java.time.Instant;
import java.util.UUID;

public class EndpointTestResponse {

    private UUID endpointId;
    private String url;
    private boolean reachable;
    private Integer statusCode;
    private Long latencyMs;
    private String message;
    private Instant testedAt;

    public EndpointTestResponse() {}

    public EndpointTestResponse(UUID endpointId, String url, boolean reachable, Integer statusCode, Long latencyMs, String message) {
        this.endpointId = endpointId;
        this.url = url;
        this.reachable = reachable;
        this.statusCode = statusCode;
        this.latencyMs = latencyMs;
        this.message = message;
        this.testedAt = Instant.now();
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public String getUrl() {
        return url;
    }

    public boolean isReachable() {
        return reachable;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTestedAt() {
        return testedAt;
    }
}
