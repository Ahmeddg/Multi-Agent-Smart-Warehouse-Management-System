package com.warehouse.simulation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiOrderSimulationIntegrationTest {
    @Test
    @Timeout(value = 110, unit = TimeUnit.SECONDS)
    void fourRobotsCompleteAllTenOrdersThroughJade() throws Exception {
        SimulationManager manager = new SimulationManager();
        try {
            manager.start(ScenarioManager.Scenario.MULTI_ROBOT_NEGOTIATION,
                    4, 10, 200);
            long firstMovementDeadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(8);
            while (SimulationEventBus.getRobotStates().values().stream()
                    .noneMatch(value -> value.contains("|MOVING|")
                            || value.contains("|DELIVERING|"))
                    && System.nanoTime() < firstMovementDeadline) {
                Thread.sleep(50);
            }
            assertTrue(SimulationEventBus.getRobotStates().values().stream()
                    .anyMatch(value -> value.contains("|MOVING|")
                            || value.contains("|DELIVERING|")));
            manager.pause();
            Thread.sleep(250);
            var positionsDuringPause = SimulationEventBus.getRobotStates().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            java.util.Map.Entry::getKey,
                            entry -> entry.getValue().split("\\|", -1)[0]));
            int generatedDuringPause = SimulationEventBus.getGeneratedOrders();
            Thread.sleep(600);
            assertEquals(positionsDuringPause,
                    SimulationEventBus.getRobotStates().entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    java.util.Map.Entry::getKey,
                                    entry -> entry.getValue().split("\\|", -1)[0])));
            assertEquals(generatedDuringPause, SimulationEventBus.getGeneratedOrders());
            manager.resume();

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
            while (!"COMPLETED".equals(SimulationEventBus.getStatus())
                    && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }

            assertEquals("COMPLETED", SimulationEventBus.getStatus(),
                    diagnostics());
            assertEquals(10, SimulationEventBus.getGeneratedOrders());
            assertEquals(10, SimulationEventBus.getCompletedOrders(),
                    String.join(System.lineSeparator(), SimulationEventBus.getEventLog()));
            assertEquals(0, SimulationEventBus.getFailedOrders());
            assertEquals(10, SimulationEventBus.getCompletedTaskCount());
            assertEquals(0, SimulationEventBus.getPendingOrders());
            assertEquals(0, SimulationEventBus.getPendingTaskCount());
            assertEquals(0, SimulationEventBus.getActiveTasks());
            assertEquals(4, SimulationEventBus.getRobotStates().size());
            assertTrue(SimulationEventBus.getNegotiations() >= 10);
            assertTrue(SimulationEventBus.getMaxConcurrentExecutingRobots() >= 2);
        } finally {
            manager.reset();
        }
    }

    private String diagnostics() {
        var robots = SimulationEventBus.getRobotStates();
        return "generated=" + SimulationEventBus.getGeneratedOrders()
                + ", successful=" + SimulationEventBus.getCompletedOrders()
                + ", failed=" + SimulationEventBus.getFailedOrders()
                + ", pendingOrders=" + SimulationEventBus.getPendingOrders()
                + ", pendingTasks=" + SimulationEventBus.getPendingTaskCount()
                + ", activeTasks=" + SimulationEventBus.getActiveTasks()
                + ", taskStates=" + SimulationEventBus.getTaskStates()
                + ", robots=" + robots
                + ", recentEvents="
                + String.join(System.lineSeparator(),
                SimulationEventBus.getEventLog().stream()
                        .skip(Math.max(0, SimulationEventBus.getEventLog().size() - 80))
                        .toList());
    }
}
