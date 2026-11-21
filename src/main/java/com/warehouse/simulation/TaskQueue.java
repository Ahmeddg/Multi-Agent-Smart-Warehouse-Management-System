package com.warehouse.simulation;

import com.warehouse.models.TaskStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class TaskQueue {
    private final Map<String, Entry> tasks = new ConcurrentHashMap<>();
    private final Map<String, String> robotAssignments = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();

    public synchronized boolean enqueue(String taskId, String content) {
        if (tasks.containsKey(taskId)) {
            return false;
        }
        Entry entry = new Entry(taskId, content);
        tasks.put(taskId, entry);
        pending.add(taskId);
        SimulationEventBus.setTaskState(taskId, TaskStatus.PENDING.name());
        return true;
    }

    public synchronized Optional<Entry> announceNext() {
        String taskId = pending.poll();
        if (taskId == null) {
            return Optional.empty();
        }
        Entry entry = tasks.get(taskId);
        if (entry == null || entry.status != TaskStatus.PENDING) {
            return announceNext();
        }
        entry.status = TaskStatus.ANNOUNCED;
        SimulationEventBus.setTaskState(taskId, entry.status.name());
        return Optional.of(entry);
    }

    public synchronized boolean assign(String taskId, String robotId) {
        Entry entry = tasks.get(taskId);
        if (entry == null || entry.status != TaskStatus.ANNOUNCED
                || robotAssignments.containsKey(robotId)) {
            return false;
        }
        entry.status = TaskStatus.ASSIGNED;
        entry.robotId = robotId;
        robotAssignments.put(robotId, taskId);
        SimulationEventBus.setTaskState(taskId, entry.status.name());
        return true;
    }

    public synchronized boolean markInProgress(String taskId, String robotId) {
        Entry entry = tasks.get(taskId);
        if (entry == null || entry.status != TaskStatus.ASSIGNED
                || !robotId.equals(entry.robotId)) {
            return false;
        }
        entry.status = TaskStatus.IN_PROGRESS;
        SimulationEventBus.setTaskState(taskId, entry.status.name());
        return true;
    }

    public synchronized boolean complete(String taskId, String robotId) {
        Entry entry = tasks.get(taskId);
        if (entry == null || entry.status != TaskStatus.IN_PROGRESS
                || !robotId.equals(entry.robotId)) {
            return false;
        }
        entry.status = TaskStatus.COMPLETED;
        robotAssignments.remove(robotId, taskId);
        SimulationEventBus.setTaskState(taskId, entry.status.name());
        return true;
    }

    public synchronized boolean fail(String taskId, String robotId) {
        Entry entry = tasks.get(taskId);
        if (entry == null || entry.status == TaskStatus.COMPLETED
                || entry.status == TaskStatus.FAILED
                || (entry.robotId != null && !entry.robotId.equals(robotId))) {
            return false;
        }
        entry.status = TaskStatus.FAILED;
        if (entry.robotId != null) {
            robotAssignments.remove(entry.robotId, taskId);
        }
        SimulationEventBus.setTaskState(taskId, entry.status.name());
        return true;
    }

    public synchronized boolean requeue(String taskId) {
        Entry entry = tasks.get(taskId);
        if (entry == null || entry.status != TaskStatus.ANNOUNCED) {
            return false;
        }
        entry.status = TaskStatus.PENDING;
        pending.add(taskId);
        SimulationEventBus.setTaskState(taskId, entry.status.name());
        return true;
    }

    public Optional<String> taskForRobot(String robotId) {
        return Optional.ofNullable(robotAssignments.get(robotId));
    }

    public synchronized Optional<Entry> get(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    public synchronized Collection<Entry> snapshot() {
        return new ArrayList<>(tasks.values());
    }

    public synchronized int pendingCount() {
        return (int) tasks.values().stream()
                .filter(entry -> entry.status == TaskStatus.PENDING).count();
    }

    public synchronized int activeCount() {
        return (int) tasks.values().stream()
                .filter(entry -> entry.status == TaskStatus.ANNOUNCED
                        || entry.status == TaskStatus.ASSIGNED
                        || entry.status == TaskStatus.IN_PROGRESS).count();
    }

    public static final class Entry {
        private final String id;
        private final String content;
        private TaskStatus status = TaskStatus.PENDING;
        private String robotId;

        private Entry(String id, String content) {
            this.id = id;
            this.content = content;
        }

        public String id() {
            return id;
        }

        public String content() {
            return content;
        }

        public TaskStatus status() {
            return status;
        }

        public String robotId() {
            return robotId;
        }
    }
}
