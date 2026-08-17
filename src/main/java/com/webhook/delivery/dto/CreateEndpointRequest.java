package com.webhook.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class CreateEndpointRequest {

    @NotBlank(message = "URL is required")
    private String url;

    private String secret; // Optional: auto-generated if omitted

    @NotEmpty(message = "At least one subscribed event type or wildcard '*' is required")
    private List<String> eventTypes;

    public CreateEndpointRequest() {}

    public CreateEndpointRequest(String url, String secret, List<String> eventTypes) {
        this.url = url;
        this.secret = secret;
        this.eventTypes = eventTypes;
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

    public List<String> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(List<String> eventTypes) {
        this.eventTypes = eventTypes;
    }
}
