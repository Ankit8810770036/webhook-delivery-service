package com.webhook.delivery.dto;

import java.time.Instant;
import java.util.List;

public class ApiErrorResponse {

    private int status;
    private String error;
    private String message;
    private String path;
    private String traceId;
    private Instant timestamp = Instant.now();
    private List<String> details;

    public ApiErrorResponse() {}

    public ApiErrorResponse(int status, String error, String message, String path, String traceId) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.traceId = traceId;
        this.timestamp = Instant.now();
    }

    public ApiErrorResponse(int status, String error, String message, String path, String traceId, List<String> details) {
        this(status, error, message, path, traceId);
        this.details = details;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public List<String> getDetails() {
        return details;
    }
}
