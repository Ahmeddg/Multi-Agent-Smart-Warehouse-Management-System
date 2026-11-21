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
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(95);
            while (!"COMPLETED".equals(SimulationEventBus.getStatus())
                    && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }

            assertEquals("COMPLETED", SimulationEventBus.getStatus());
            assertEquals(10, SimulationEventBus.getGeneratedOrders());
            assertEquals(10, SimulationEventBus.getCompletedOrders());
            assertEquals(0, SimulationEventBus.getFailedOrders());
            assertEquals(10, SimulationEventBus.getCompletedTaskCount());
            assertEquals(0, SimulationEventBus.getPendingOrders());
            assertEquals(0, SimulationEventBus.getPendingTaskCount());
            assertEquals(0, SimulationEventBus.getActiveTasks());
            assertEquals(4, SimulationEventBus.getRobotStates().size());
            assertTrue(SimulationEventBus.getNegotiations() >= 10);
        } finally {
            manager.reset();
        }
    }
}
