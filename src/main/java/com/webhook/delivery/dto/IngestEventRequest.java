package com.webhook.delivery.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class IngestEventRequest {

    @NotBlank(message = "eventId is required")
    private String eventId;

    @NotBlank(message = "type is required")
    private String type;

    @NotNull(message = "payload is required")
    private JsonNode payload;

    public IngestEventRequest() {}

    public IngestEventRequest(String eventId, String type, JsonNode payload) {
        this.eventId = eventId;
        this.type = type;
        this.payload = payload;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }
}
