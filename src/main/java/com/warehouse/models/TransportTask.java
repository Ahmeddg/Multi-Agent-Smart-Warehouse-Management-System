package com.warehouse.models;

import com.warehouse.environment.Position;

import java.util.Objects;

public final class TransportTask {
    private final String id;
    private final Position pickup;
    private final Position delivery;
    private final int priority;
    private volatile TaskStatus status;

    public TransportTask(String id, Position pickup, Position delivery, int priority) {
        this.id = Objects.requireNonNull(id);
        this.pickup = Objects.requireNonNull(pickup);
        this.delivery = Objects.requireNonNull(delivery);
        this.priority = priority;
        this.status = TaskStatus.PENDING;
    }

    public String getId() {
        return id;
    }

    public Position getPickup() {
        return pickup;
    }

    public Position getDelivery() {
        return delivery;
    }

    public int getPriority() {
        return priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = Objects.requireNonNull(status);
    }
}
