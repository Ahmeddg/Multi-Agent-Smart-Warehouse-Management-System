package com.warehouse.models;

public enum OrderStatus {
    PENDING,
    VALIDATING,
    RESERVED,
    ALLOCATING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
