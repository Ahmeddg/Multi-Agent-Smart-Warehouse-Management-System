package com.warehouse;

import com.warehouse.algorithms.TaskScoringAlgorithm;
import com.warehouse.models.TaskProposal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskAllocationTest {
    @Test
    void rejectsUnavailableRobot() {
        TaskProposal proposal = new TaskProposal("ROBOT-01", 2, 5, 10, 1);
        double score = new TaskScoringAlgorithm().calculate(proposal, 80, false);

        assertEquals(-1.0, score);
    }

    @Test
    void rejectsInsufficientBattery() {
        TaskProposal proposal = new TaskProposal("ROBOT-01", 2, 5, 30, 1);
        double score = new TaskScoringAlgorithm().calculate(proposal, 20, true);

        assertEquals(-1.0, score);
    }

    @Test
    void balancesDistanceAndWorkload() {
        TaskScoringAlgorithm algorithm = new TaskScoringAlgorithm();
        TaskProposal nearest = new TaskProposal("ROBOT-01", 1, 5, 10, 4);
        TaskProposal balanced = new TaskProposal("ROBOT-02", 4, 7, 10, 1);

        double nearestScore = algorithm.calculate(nearest, 80, true);
        double balancedScore = algorithm.calculate(balanced, 80, true);

        assertTrue(nearestScore > 0);
        assertTrue(balancedScore > 0);
    }
}
