package com.warehouse;

import com.warehouse.algorithms.DeadlockDetector;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficManagementTest {
    @Test
    void detectsCircularWaitingDependencies() {
        Map<String, String> waitingFor = Map.of(
                "ROBOT-01", "ROBOT-02",
                "ROBOT-02", "ROBOT-03",
                "ROBOT-03", "ROBOT-01");

        List<String> cycle = new DeadlockDetector().detectCycle(waitingFor);

        assertFalse(cycle.isEmpty());
        assertTrue(cycle.stream().allMatch(
                robot -> Arrays.asList("ROBOT-01", "ROBOT-02", "ROBOT-03")
                        .contains(robot)));
    }

    @Test
    void returnsNoCycleForLinearWaiting() {
        Map<String, String> waitingFor = Map.of(
                "ROBOT-01", "ROBOT-02",
                "ROBOT-02", "CELL-01");

        assertTrue(new DeadlockDetector().detectCycle(waitingFor).isEmpty());
    }
}
