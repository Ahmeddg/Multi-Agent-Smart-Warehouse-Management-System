package com.warehouse.models;

import java.util.Objects;

public final class TaskProposal {
    private final String robotId;
    private final double distance;
    private final double estimatedTime;
    private final double batteryCost;
    private final double workload;
    private double score;

    public TaskProposal(String robotId,
                        double distance,
                        double estimatedTime,
                        double batteryCost,
                        double workload) {
        this.robotId = Objects.requireNonNull(robotId);
        this.distance = distance;
        this.estimatedTime = estimatedTime;
        this.batteryCost = batteryCost;
        this.workload = workload;
    }

    public String getRobotId() {
        return robotId;
    }

    public double getDistance() {
        return distance;
    }

    public double getEstimatedTime() {
        return estimatedTime;
    }

    public double getBatteryCost() {
        return batteryCost;
    }

    public double getWorkload() {
        return workload;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
