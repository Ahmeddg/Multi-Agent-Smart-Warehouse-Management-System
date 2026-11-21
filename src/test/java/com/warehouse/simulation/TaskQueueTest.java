package com.warehouse.simulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskQueueTest {
    private TaskQueue queue;

    @BeforeEach
    void setUp() {
        SimulationEventBus.reset();
        queue = new TaskQueue();
    }

    @Test
    void reusesRobotForConsecutiveTasksAfterCompletion() {
        assertTrue(queue.enqueue("T01", "{\"taskId\":\"T01\"}"));
        assertTrue(queue.enqueue("T02", "{\"taskId\":\"T02\"}"));

        TaskQueue.Entry first = queue.announceNext().orElseThrow();
        assertTrue(queue.assign(first.id(), "ROBOT-01"));
        assertTrue(queue.markInProgress(first.id(), "ROBOT-01"));
        assertTrue(queue.complete(first.id(), "ROBOT-01"));
        assertTrue(queue.taskForRobot("ROBOT-01").isEmpty());

        TaskQueue.Entry second = queue.announceNext().orElseThrow();
        assertEquals("T02", second.id());
        assertTrue(queue.assign(second.id(), "ROBOT-01"));
        assertTrue(queue.markInProgress(second.id(), "ROBOT-01"));
        assertTrue(queue.complete(second.id(), "ROBOT-01"));
        assertEquals(0, queue.activeCount());
    }

    @Test
    void neverAssignsOneTaskToTwoRobotsConcurrently() throws Exception {
        queue.enqueue("T01", "{\"taskId\":\"T01\"}");
        TaskQueue.Entry task = queue.announceNext().orElseThrow();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger assignments = new AtomicInteger();
        try {
            for (String robot : new String[]{"ROBOT-01", "ROBOT-02"}) {
                executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    if (queue.assign(task.id(), robot)) {
                        assignments.incrementAndGet();
                    }
                    return null;
                });
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            assertEquals(1, assignments.get());
            assertEquals(1, queue.snapshot().stream()
                    .filter(entry -> entry.robotId() != null).count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void refusesDuplicateCompletionAndTracksPendingStates() {
        queue.enqueue("T01", "{\"taskId\":\"T01\"}");
        TaskQueue.Entry task = queue.announceNext().orElseThrow();
        assertEquals(1, queue.activeCount());
        assertTrue(queue.assign(task.id(), "ROBOT-01"));
        assertTrue(queue.markInProgress(task.id(), "ROBOT-01"));
        assertTrue(queue.complete(task.id(), "ROBOT-01"));
        assertFalse(queue.complete(task.id(), "ROBOT-01"));
        assertEquals("COMPLETED",
                SimulationEventBus.getTaskStates().get("T01"));
    }
}
