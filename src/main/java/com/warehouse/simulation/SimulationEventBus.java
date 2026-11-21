package com.warehouse.simulation;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import com.warehouse.environment.Position;

public final class SimulationEventBus {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private static final Map<String, String> agentStates = new ConcurrentHashMap<>();
    private static final Map<String, String> robotStates = new ConcurrentHashMap<>();
    private static final Map<String, String> taskStates = new ConcurrentHashMap<>();
    private static final Map<String, String> cellReservations = new ConcurrentHashMap<>();
    private static final Map<String, Long> robotStateSince = new ConcurrentHashMap<>();
    private static final Map<String, Long> robotBusyMillis = new ConcurrentHashMap<>();
    private static final Map<String, Long> taskStartedAt = new ConcurrentHashMap<>();
    private static volatile String status = "READY";
    private static volatile int completedOrders;
    private static volatile int pendingOrders;
    private static volatile int activeTasks;
    private static volatile int totalOrders;
    private static volatile int generatedOrders;
    private static volatile int failedTasks;
    private static volatile int failedOrders;
    private static volatile int collisions;
    private static volatile int deadlocksResolved;
    private static volatile int negotiations;
    private static volatile int activeNegotiations;
    private static volatile long totalTaskMillis;
    private static volatile int completedTaskCount;
    private static volatile long startedAt;

    private SimulationEventBus() {
    }

    public static synchronized void reset() {
        completedOrders = 0;
        pendingOrders = 0;
        activeTasks = 0;
        totalOrders = 0;
        generatedOrders = 0;
        failedTasks = 0;
        failedOrders = 0;
        collisions = 0;
        deadlocksResolved = 0;
        negotiations = 0;
        activeNegotiations = 0;
        totalTaskMillis = 0;
        completedTaskCount = 0;
        startedAt = System.nanoTime();
        agentStates.clear();
        robotStates.clear();
        taskStates.clear();
        cellReservations.clear();
        robotStateSince.clear();
        robotBusyMillis.clear();
        taskStartedAt.clear();
        status = "READY";
    }

    public static synchronized void configureWorkload(int robotCount, int orderCount) {
        totalOrders = orderCount;
        startedAt = System.nanoTime();
    }

    public static synchronized void orderGenerated() {
        generatedOrders = Math.min(totalOrders, generatedOrders + 1);
        pendingOrders++;
    }

    public static void subscribe(Consumer<String> listener) {
        listeners.add(listener);
    }

    public static void publishLog(String sender, String receiver, String type, String content) {
        String entry = "[" + TIME.format(LocalTime.now()) + "] [" + sender + "] -> ["
                + receiver + "]: " + type + " " + content;
        for (Consumer<String> listener : listeners) {
            listener.accept(entry);
        }
    }

    public static void publishSystem(String message) {
        for (Consumer<String> listener : listeners) {
            listener.accept("[" + TIME.format(LocalTime.now()) + "] [SYSTEM]: " + message);
        }
    }

    public static void setStatus(String value) {
        status = value;
        publishSystem("Simulation " + value);
    }

    public static void setAgentState(String agent, String type, String state, String activity) {
        agentStates.put(agent, type + "|" + state + "|" + activity);
    }

    public static void setRobotState(String robotId, String position, String status,
                                     String task, String destination, String battery,
                                     int completed, boolean carrying, String path) {
        long now = System.nanoTime();
        String previous = robotStates.get(robotId);
        if (previous != null) {
            String previousStatus = previous.split("\\|", -1)[1];
            long since = robotStateSince.getOrDefault(robotId, now);
            if (isBusy(previousStatus)) {
                robotBusyMillis.merge(robotId, (now - since) / 1_000_000L, Long::sum);
            }
        }
        robotStateSince.put(robotId, now);
        robotStates.put(robotId, position + "|" + status + "|" + task + "|" + destination
                + "|" + battery + "|" + completed + "|" + carrying + "|" + path);
    }

    private static boolean isBusy(String state) {
        return "MOVING".equals(state) || "NEGOTIATING".equals(state)
                || "PICKING_UP".equals(state) || "DELIVERING".equals(state);
    }

    public static void setTaskState(String taskId, String state) {
        String previous = taskStates.put(taskId, state);
        activeTasks = (int) taskStates.values().stream()
                .filter(value -> value.equals("ANNOUNCED") || value.equals("ASSIGNED")
                        || value.equals("IN_PROGRESS")).count();
        if (previous == null || "PENDING".equals(previous)) {
            taskStartedAt.putIfAbsent(taskId, System.nanoTime());
        }
        if ("COMPLETED".equals(state) && !"COMPLETED".equals(previous)) {
            Long started = taskStartedAt.remove(taskId);
            if (started != null) {
                totalTaskMillis += (System.nanoTime() - started) / 1_000_000L;
                completedTaskCount++;
            }
        }
    }

    public static Map<String, String> getTaskStates() {
        return Map.copyOf(taskStates);
    }

    public static Map<String, String> getCellReservations() {
        return Map.copyOf(cellReservations);
    }

    public static void reserveCell(Position position, String robotId) {
        cellReservations.put(position.getX() + "," + position.getY(), robotId);
    }

    public static void releaseCell(Position position, String robotId) {
        cellReservations.remove(position.getX() + "," + position.getY(), robotId);
    }

    public static Map<String, String> getAgentStates() {
        return Map.copyOf(agentStates);
    }

    public static Map<String, String> getRobotStates() {
        return Map.copyOf(robotStates);
    }

    public static String getStatus() {
        return status;
    }

    public static boolean isRunning() {
        return "RUNNING".equals(status);
    }

    public static synchronized void incrementCompletedOrders() {
        completedOrders++;
        pendingOrders = Math.max(0, pendingOrders - 1);
    }

    public static synchronized void incrementPendingOrders() {
        pendingOrders++;
    }

    public static synchronized void incrementFailedOrders() {
        failedOrders++;
        pendingOrders = Math.max(0, pendingOrders - 1);
    }

    public static synchronized void setActiveTasks(int value) {
        activeTasks = value;
    }

    public static synchronized void incrementFailedTasks() {
        failedTasks++;
        activeTasks = Math.max(0, activeTasks - 1);
    }

    public static synchronized void incrementCollisions() {
        collisions++;
    }

    public static synchronized void incrementDeadlocksResolved() {
        deadlocksResolved++;
    }

    public static synchronized void negotiationStarted() {
        negotiations++;
        activeNegotiations++;
    }

    public static synchronized void negotiationFinished() {
        activeNegotiations = Math.max(0, activeNegotiations - 1);
    }

    public static int getCompletedOrders() {
        return completedOrders;
    }

    public static int getPendingOrders() {
        return pendingOrders;
    }

    public static int getActiveTasks() {
        return activeTasks;
    }

    public static int getFailedTasks() {
        return failedTasks;
    }

    public static int getCollisions() {
        return collisions;
    }

    public static int getDeadlocksResolved() {
        return deadlocksResolved;
    }

    public static int getTotalOrders() {
        return totalOrders;
    }

    public static int getGeneratedOrders() {
        return generatedOrders;
    }

    public static int getFailedOrders() {
        return failedOrders;
    }

    public static int getPendingTaskCount() {
        return (int) taskStates.values().stream()
                .filter(value -> "PENDING".equals(value)).count();
    }

    public static int getCompletedTaskCount() {
        return (int) taskStates.values().stream()
                .filter(value -> "COMPLETED".equals(value)).count();
    }

    public static int getNegotiations() {
        return negotiations;
    }

    public static double getAverageCompletionSeconds() {
        return completedTaskCount == 0 ? 0
                : totalTaskMillis / 1000.0 / completedTaskCount;
    }

    public static double getRobotUtilizationPercent() {
        long elapsed = Math.max(1, (System.nanoTime() - startedAt) / 1_000_000L);
        long busy = robotBusyMillis.values().stream().mapToLong(Long::longValue).sum();
        long now = System.nanoTime();
        for (Map.Entry<String, Long> entry : robotStateSince.entrySet()) {
            String state = robotStates.get(entry.getKey());
            if (state != null && isBusy(state.split("\\|", -1)[1])) {
                busy += (now - entry.getValue()) / 1_000_000L;
            }
        }
        int robots = Math.max(1, robotStates.size());
        return Math.min(100.0, 100.0 * busy / (elapsed * robots));
    }

    public static boolean isWorkloadComplete() {
        if (totalOrders <= 0 || generatedOrders < totalOrders
                || completedOrders + failedOrders < totalOrders
                || pendingOrders != 0 || activeTasks != 0 || activeNegotiations != 0) {
            return false;
        }
        return robotStates.values().stream().allMatch(value -> {
            String state = value.split("\\|", -1)[1];
            return "IDLE".equals(state);
        });
    }

    public static void finishSimulationIfComplete() {
        if (isRunning() && isWorkloadComplete()) {
            status = "COMPLETED";
            publishSystem("SIMULATION COMPLETED | Orders: " + totalOrders
                    + " | Successful: " + completedOrders
                    + " | Failed: " + failedOrders
                    + " | Tasks: " + totalOrders
                    + " | Average: " + String.format("%.2f", getAverageCompletionSeconds())
                    + "s | Robot utilization: "
                    + String.format("%.1f", getRobotUtilizationPercent())
                    + "% | Negotiations: " + negotiations
                    + " | Conflicts: " + collisions
                    + " | Deadlocks resolved: " + deadlocksResolved);
        }
    }
}
