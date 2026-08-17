package com.webhook.delivery.domain;

public enum DeliveryStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    DEAD_LETTERED,
    CANCELLED
}
