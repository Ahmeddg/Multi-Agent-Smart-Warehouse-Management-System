package com.warehouse.agents;

import com.warehouse.communication.AgentService;
import com.warehouse.communication.MessageProtocol;
import com.warehouse.algorithms.AStarPathfinder;
import com.warehouse.environment.Warehouse;
import com.warehouse.environment.Position;
import com.warehouse.models.RobotState;
import com.warehouse.models.RobotStatus;
import com.warehouse.models.TransportTask;
import com.warehouse.models.TaskStatus;
import com.warehouse.simulation.SimulationEventBus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.List;

public class RobotAgent extends Agent {
    private String robotId;
    private RobotState state;
    private Warehouse warehouse;
    private final AStarPathfinder pathfinder = new AStarPathfinder();
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Position> path = new ArrayList<>();
    private int pathIndex;
    private int completedTasks;
    private int speedPercent = 100;
    private String activeOrderId;
    private String activeTaskId;
    private Position deliveryPosition;
    private boolean carrying;
    private boolean movementPending;
    private boolean approachingDelivery;

    @Override
    protected void setup() {
        Object[] arguments = getArguments();
        robotId = arguments != null && arguments.length > 0
                ? String.valueOf(arguments[0])
                : getLocalName();
        Position start = arguments != null && arguments.length > 2
                && arguments[2] instanceof Position position
                ? position : new Position(1, 1);
        double battery = arguments != null && arguments.length > 3
                ? Double.parseDouble(String.valueOf(arguments[3])) : 100.0;
        speedPercent = arguments != null && arguments.length > 4
                ? Integer.parseInt(String.valueOf(arguments[4])) : 100;
        warehouse = arguments != null && arguments.length > 1
                && arguments[1] instanceof Warehouse value
                ? value : null;
        state = new RobotState(robotId, start, battery);
        state.setStatus(RobotStatus.IDLE);
        if (warehouse != null) {
            warehouse.getGrid().occupy(start, robotId);
        }

        AgentService.register(this,
                "robot-" + robotId,
                MessageProtocol.ROBOT_SERVICE);
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage message = myAgent.receive();
                if (message == null) {
                    block();
                    return;
                }
                handleMessage(message);
            }
        });
        int interval = Math.max(150, 700 - (speedPercent * 4));
        addBehaviour(new TickerBehaviour(this, interval) {
            @Override
            protected void onTick() {
                requestNextMove();
                updateEventState();
            }
        });
        updateEventState();
    }

    private void handleMessage(ACLMessage message) {
        if (message.getPerformative() == ACLMessage.CFP) {
            handleCfp(message);
        } else if (message.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
            handleAccept(message);
        } else if (message.getPerformative() == ACLMessage.REJECT_PROPOSAL) {
            state.setStatus(RobotStatus.IDLE);
            sendAvailable();
        } else if (message.getPerformative() == ACLMessage.AGREE
                && message.getContent() != null
                && message.getContent().contains("MOVEMENT_GRANTED")) {
            movementPending = false;
            moveOneStep();
        } else if (message.getPerformative() == ACLMessage.REFUSE
                && message.getContent() != null
                && message.getContent().contains("CELL_OCCUPIED")) {
            movementPending = false;
            state.setStatus(RobotStatus.WAITING);
            SimulationEventBus.incrementCollisions();
        } else if (message.getPerformative() == ACLMessage.INFORM
                && message.getContent() != null
                && message.getContent().contains("CELL_AVAILABLE")) {
            if (state.getStatus() == RobotStatus.WAITING) {
                state.setStatus(RobotStatus.MOVING);
            }
        }
    }

    private void handleCfp(ACLMessage message) {
        try {
            JsonNode task = mapper.readTree(message.getContent());
            Position pickup = new Position(
                    task.path("pickup").path("x").asInt(),
                    task.path("pickup").path("y").asInt());
            Position delivery = new Position(
                    task.path("delivery").path("x").asInt(),
                    task.path("delivery").path("y").asInt());
            List<Position> pickupPath = warehouse == null
                    ? List.of(state.getPosition(), pickup)
                    : pathfinder.findPath(warehouse.getGrid(), state.getPosition(), pickup);
            List<Position> deliveryPath = warehouse == null
                    ? List.of(pickup, delivery)
                    : pathfinder.findPath(warehouse.getGrid(), pickup, delivery);
            if (pickupPath.isEmpty() || deliveryPath.isEmpty()) {
                refuseTask(message, "NO_ROUTE");
                return;
            }
            int distance = Math.max(0, pickupPath.size() - 1)
                    + Math.max(0, deliveryPath.size() - 1);
            int batteryCost = 8 + distance / 3;
            if (state.getBatteryLevel() <= batteryCost + 5
                    || state.getStatus() != RobotStatus.IDLE) {
                refuseTask(message, "BATTERY_OR_AVAILABILITY");
                return;
            }
            state.setStatus(RobotStatus.NEGOTIATING);
            ACLMessage proposal = message.createReply();
            proposal.setPerformative(ACLMessage.PROPOSE);
            proposal.setLanguage("JSON");
            proposal.setContent("{\"robotId\":\"" + robotId
                    + "\",\"distance\":" + distance
                    + ",\"estimatedTime\":" + (distance * 2)
                    + ",\"batteryCost\":" + batteryCost
                    + ",\"batteryLevel\":" + state.getBatteryLevel()
                    + ",\"workload\":" + (completedTasks == 0 ? 0 : completedTasks) + "}");
            send(proposal);
            SimulationEventBus.publishLog(robotId, "ALLOCATION-AGENT",
                    "PROPOSE", "COST=" + (distance * 2));
        } catch (Exception exception) {
            refuseTask(message, "INVALID_TASK");
        }
    }

    private void refuseTask(ACLMessage message, String reason) {
        ACLMessage refuse = message.createReply();
        refuse.setPerformative(ACLMessage.REFUSE);
        refuse.setContent("{\"type\":\"TASK_REFUSED\",\"robotId\":\""
                + robotId + "\",\"reason\":\"" + reason + "\"}");
        send(refuse);
    }

    private void handleAccept(ACLMessage message) {
        try {
            JsonNode task = mapper.readTree(message.getContent());
            String taskId = task.path("taskId").asText();
            Position pickup = new Position(
                    task.path("pickup").path("x").asInt(),
                    task.path("pickup").path("y").asInt());
            Position delivery = new Position(
                    task.path("delivery").path("x").asInt(),
                    task.path("delivery").path("y").asInt());
            activeOrderId = task.path("orderId").asText();
            state.setTask(new TransportTask(task.path("taskId").asText(),
                    pickup, delivery, 1));
            activeTaskId = taskId;
            deliveryPosition = delivery;
            carrying = false;
            approachingDelivery = false;
            if (warehouse == null) {
                return;
            }
            if (!preparePath(pickup)) {
                failActiveTask("NO_PATH_TO_PICKUP");
                return;
            }
            state.setDestination(pickup);
            state.setStatus(RobotStatus.MOVING);
            if (path.size() == 1) {
                pickUpProduct();
            }
            SimulationEventBus.publishLog(robotId, "ALLOCATION-AGENT",
                    "INFORM", "TASK_STARTED " + taskId);
            ACLMessage started = new ACLMessage(ACLMessage.INFORM);
            started.addReceiver(new AID("allocation-agent", AID.ISLOCALNAME));
            started.setConversationId(taskId);
            started.setLanguage("JSON");
            started.setContent("{\"type\":\"TASK_STARTED\",\"taskId\":\""
                    + taskId + "\",\"robotId\":\"" + robotId + "\"}");
            send(started);
        } catch (Exception exception) {
            state.setStatus(RobotStatus.IDLE);
        }
    }

    private void requestNextMove() {
        if (!SimulationEventBus.isRunning()) {
            return;
        }
        if (state.getStatus() == RobotStatus.PICKING_UP) {
            state.setStatus(RobotStatus.DELIVERING);
        }
        if ((state.getStatus() != RobotStatus.MOVING
                && state.getStatus() != RobotStatus.DELIVERING)
                || warehouse == null) {
            return;
        }
        if (pathIndex >= path.size()) {
            if (approachingDelivery) {
                completeTask();
            } else {
                pickUpProduct();
            }
            return;
        }
        if (movementPending) {
            return;
        }
        Position next = path.get(pathIndex);
        if (!warehouse.getGrid().contains(next)
                || !warehouse.getGrid().isTraversable(next)) {
            state.setStatus(RobotStatus.BLOCKED);
            recalculatePath();
            return;
        }
        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
        request.addReceiver(new AID("traffic-agent", AID.ISLOCALNAME));
        request.setConversationId(robotId);
        request.setLanguage("JSON");
        request.setContent("{\"type\":\"MOVEMENT_REQUEST\",\"x\":"
                + next.getX() + ",\"y\":" + next.getY() + "}");
        send(request);
        movementPending = true;
    }

    private void moveOneStep() {
        if (!SimulationEventBus.isRunning() || pathIndex >= path.size()
                || warehouse == null) {
            return;
        }
        Position from = state.getPosition();
        Position next = path.get(pathIndex);
        if (warehouse.getGrid().moveRobot(robotId, from, next)) {
            ACLMessage release = new ACLMessage(ACLMessage.REQUEST);
            release.addReceiver(new AID("traffic-agent", AID.ISLOCALNAME));
            release.setConversationId(robotId);
            release.setLanguage("JSON");
            release.setContent("{\"type\":\"MOVEMENT_RELEASE\",\"x\":"
                    + from.getX() + ",\"y\":" + from.getY() + "}");
            send(release);
            state.setPosition(next);
            state.setBatteryLevel(Math.max(0, state.getBatteryLevel() - 0.08));
            pathIndex++;
            if (pathIndex >= path.size()) {
                if (!approachingDelivery) {
                    pickUpProduct();
                } else {
                    completeTask();
                }
            }
        } else {
            state.setStatus(RobotStatus.WAITING);
            SimulationEventBus.incrementCollisions();
            releaseReservation(next);
        }
    }

    private void releaseReservation(Position position) {
        ACLMessage release = new ACLMessage(ACLMessage.REQUEST);
        release.addReceiver(new AID("traffic-agent", AID.ISLOCALNAME));
        release.setLanguage("JSON");
        release.setContent("{\"type\":\"MOVEMENT_RELEASE\",\"x\":"
                + position.getX() + ",\"y\":" + position.getY() + "}");
        send(release);
    }

    private boolean preparePath(Position destination) {
        path.clear();
        path.addAll(pathfinder.findPath(warehouse.getGrid(),
                state.getPosition(), destination));
        pathIndex = 1;
        return !path.isEmpty();
    }

    private void pickUpProduct() {
        state.setStatus(RobotStatus.PICKING_UP);
        carrying = true;
        approachingDelivery = true;
        state.setDestination(deliveryPosition);
        SimulationEventBus.publishLog(robotId, "INVENTORY-AGENT",
                "INFORM", "PRODUCT_PICKED_UP " + activeOrderId);
        if (!preparePath(deliveryPosition)) {
            failActiveTask("NO_PATH_TO_DELIVERY");
        }
    }

    private void recalculatePath() {
        if (state.getDestination() == null || warehouse == null) {
            return;
        }
        if (!preparePath(state.getDestination())) {
            failActiveTask("ROUTE_BLOCKED");
            return;
        }
        state.setStatus(RobotStatus.MOVING);
    }

    private void completeTask() {
        state.setStatus(RobotStatus.IDLE);
        completedTasks++;
        if (state.getTask() != null) {
            state.getTask().setStatus(TaskStatus.COMPLETED);
        }
        String orderId = activeOrderId == null ? "UNKNOWN" : activeOrderId;
        ACLMessage completed = taskNotification("TASK_COMPLETED", orderId, "");
        completed.addReceiver(new AID("allocation-agent", AID.ISLOCALNAME));
        completed.addReceiver(new AID("order-agent", AID.ISLOCALNAME));
        completed.setConversationId(activeTaskId);
        send(completed);
        state.setTask(null);
        activeOrderId = null;
        activeTaskId = null;
        state.setDestination(null);
        carrying = false;
        approachingDelivery = false;
        sendAvailable();
    }

    private void failActiveTask(String reason) {
        if (activeTaskId == null) {
            state.setStatus(RobotStatus.IDLE);
            return;
        }
        ACLMessage failed = taskNotification("TASK_FAILED",
                activeOrderId == null ? "" : activeOrderId, reason);
        failed.addReceiver(new AID("allocation-agent", AID.ISLOCALNAME));
        failed.setConversationId(activeTaskId);
        send(failed);
        state.setTask(null);
        state.setDestination(null);
        activeTaskId = null;
        activeOrderId = null;
        carrying = false;
        state.setStatus(RobotStatus.IDLE);
        sendAvailable();
    }

    private ACLMessage taskNotification(String type, String orderId, String reason) {
        ACLMessage notification = new ACLMessage(ACLMessage.INFORM);
        notification.setLanguage("JSON");
        notification.setContent("{\"type\":\"" + type
                + "\",\"taskId\":\"" + activeTaskId
                + "\",\"orderId\":\"" + orderId
                + "\",\"robotId\":\"" + robotId
                + "\",\"reason\":\"" + reason + "\"}");
        return notification;
    }

    private void sendAvailable() {
        ACLMessage available = new ACLMessage(ACLMessage.INFORM);
        available.addReceiver(new AID("allocation-agent", AID.ISLOCALNAME));
        available.setContent("{\"type\":\"ROBOT_AVAILABLE\",\"robotId\":\""
                + robotId + "\"}");
        send(available);
    }

    private void updateEventState() {
        SimulationEventBus.setRobotState(robotId,
                state.getPosition().getX() + "," + state.getPosition().getY(),
                state.getStatus().name(),
                state.getTask() == null ? "AVAILABLE" : String.valueOf(state.getTask().getId()),
                state.getDestination() == null ? "-" : state.getDestination().toString(),
                String.format("%.1f%%", state.getBatteryLevel()),
                completedTasks,
                carrying,
                path.isEmpty() ? "-" : path.subList(
                        Math.min(pathIndex, path.size()),
                        path.size()).toString());
    }

    @Override
    protected void takeDown() {
        AgentService.deregister(this);
    }
}
