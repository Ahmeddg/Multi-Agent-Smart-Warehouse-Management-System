package com.warehouse.models;

import com.warehouse.environment.Position;

import java.util.Objects;

public final class RobotState {
    private final String robotId;
    private volatile Position position;
    private volatile Position destination;
    private volatile double batteryLevel;
    private volatile RobotStatus status;
    private volatile boolean carrying;
    private volatile TransportTask task;

    public RobotState(String robotId, Position position, double batteryLevel) {
        this.robotId = Objects.requireNonNull(robotId);
        this.position = Objects.requireNonNull(position);
        this.batteryLevel = batteryLevel;
        this.status = RobotStatus.IDLE;
    }

    public String getRobotId() {
        return robotId;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = Objects.requireNonNull(position);
    }

    public Position getDestination() {
        return destination;
    }

    public void setDestination(Position destination) {
        this.destination = destination;
    }

    public double getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(double batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public RobotStatus getStatus() {
        return status;
    }

    public void setStatus(RobotStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public boolean isCarrying() {
        return carrying;
    }

    public void setCarrying(boolean carrying) {
        this.carrying = carrying;
    }

    public TransportTask getTask() {
        return task;
    }

    public void setTask(TransportTask task) {
        this.task = task;
    }
}
