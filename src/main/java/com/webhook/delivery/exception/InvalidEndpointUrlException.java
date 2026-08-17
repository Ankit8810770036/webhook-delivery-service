package com.webhook.delivery.exception;

public class InvalidEndpointUrlException extends RuntimeException {
    public InvalidEndpointUrlException(String message) {
        super(message);
    }
}
