package com.warehouse.simulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEventBusTest {
    @BeforeEach
    void reset() {
        SimulationEventBus.reset();
    }

    @Test
    void completesOnlyAfterOrdersTasksAndRobotsAreIdle() {
        SimulationEventBus.configureWorkload(1, 1);
        SimulationEventBus.orderGenerated();
        SimulationEventBus.setRobotState("ROBOT-01", "1,1", "IDLE",
                "AVAILABLE", "-", "100%", 0, false, "-");
        SimulationEventBus.setTaskState("TASK-01", "PENDING");
        assertFalse(SimulationEventBus.isWorkloadComplete());

        SimulationEventBus.setTaskState("TASK-01", "ANNOUNCED");
        SimulationEventBus.setTaskState("TASK-01", "ASSIGNED");
        SimulationEventBus.setTaskState("TASK-01", "IN_PROGRESS");
        SimulationEventBus.setRobotState("ROBOT-01", "4,4", "MOVING",
                "TASK-01", "(8,8)", "90%", 0, true, "[(5,4)]");
        SimulationEventBus.setTaskState("TASK-01", "COMPLETED");
        SimulationEventBus.incrementCompletedOrders();
        assertFalse(SimulationEventBus.isWorkloadComplete());

        SimulationEventBus.setRobotState("ROBOT-01", "8,8", "IDLE",
                "AVAILABLE", "-", "85%", 1, false, "-");
        assertTrue(SimulationEventBus.isWorkloadComplete());
        SimulationEventBus.finishSimulationIfComplete();
        assertEquals("COMPLETED", SimulationEventBus.getStatus());
    }

    @Test
    void managerPauseResumeAndResetUpdateSimulationState() throws Exception {
        SimulationManager manager = new SimulationManager();
        manager.pause();
        assertEquals("PAUSED", SimulationEventBus.getStatus());
        manager.resume();
        assertEquals("RUNNING", SimulationEventBus.getStatus());
        manager.reset();
        assertEquals("RESET", SimulationEventBus.getStatus());
        assertEquals(0, SimulationEventBus.getTaskStates().size());
        assertTrue(SimulationEventBus.getRobotStates().isEmpty());
    }
}
