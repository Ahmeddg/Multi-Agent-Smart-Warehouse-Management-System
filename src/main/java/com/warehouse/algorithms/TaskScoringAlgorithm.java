package com.warehouse.algorithms;

import com.warehouse.models.TaskProposal;

public class TaskScoringAlgorithm {
    public double calculate(TaskProposal proposal,
                            double batteryLevel,
                            boolean available) {
        if (!available || batteryLevel <= proposal.getBatteryCost()) {
            return -1.0;
        }

        double distanceScore = 1.0 / (1.0 + proposal.getDistance());
        double timeScore = 1.0 / (1.0 + proposal.getEstimatedTime());
        double batteryScore = Math.max(0.0,
                (batteryLevel - proposal.getBatteryCost()) / 100.0);
        double workloadScore = 1.0 / (1.0 + proposal.getWorkload());

        return 0.30 * distanceScore
                + 0.20 * timeScore
                + 0.20 * batteryScore
                + 0.15 * workloadScore
                + 0.15 * batteryScore;
    }
}
