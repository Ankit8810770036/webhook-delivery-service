package com.webhook.delivery.exception;

public class DeliveryNotRedrivableException extends RuntimeException {
    public DeliveryNotRedrivableException(String message) {
        super(message);
    }
}
