package com.webhook.delivery.exception;

public class TenantMissingException extends RuntimeException {
    public TenantMissingException(String message) {
        super(message);
    }
}
