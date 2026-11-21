package com.warehouse.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.algorithms.TaskScoringAlgorithm;
import com.warehouse.communication.AgentService;
import com.warehouse.communication.MessageProtocol;
import com.warehouse.models.TaskProposal;
import com.warehouse.simulation.SimulationEventBus;
import com.warehouse.simulation.TaskQueue;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TaskAllocationAgent extends Agent {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(TaskAllocationAgent.class);
    private final TaskQueue tasks = new TaskQueue();
    private final Map<String, TaskProposal> proposals = new HashMap<>();
    private final Set<String> availableRobots = new HashSet<>();
    private final TaskScoringAlgorithm scoring = new TaskScoringAlgorithm();
    private final ObjectMapper mapper = new ObjectMapper();
    private String activeTaskId;
    private String activeConversationId;
    private String activeTaskContent;
    private boolean negotiationTimerScheduled;

    @Override
    protected void setup() {
        AgentService.register(this, "task-allocation-agent",
                MessageProtocol.ALLOCATION_SERVICE);
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
        addBehaviour(new TickerBehaviour(this, 500) {
            @Override
            protected void onTick() {
                startNextNegotiation();
            }
        });
        SimulationEventBus.setAgentState("allocation-agent", "Allocation", "ACTIVE",
                "Monitoring task queue");
    }

    private void handleMessage(ACLMessage message) {
        String type = extractType(message.getContent());
        if (message.getPerformative() == ACLMessage.REQUEST
                && "TRANSPORT_TASK".equals(type)) {
            enqueueTask(message);
        } else if (message.getPerformative() == ACLMessage.PROPOSE
                && (activeConversationId == null
                || !activeConversationId.equals(message.getConversationId()))) {
            sendDecision(message.getSender(), ACLMessage.REJECT_PROPOSAL,
                    message.getConversationId(), "{\"type\":\"STALE_NEGOTIATION\"}");
        } else if (activeConversationId != null
                && activeConversationId.equals(message.getConversationId())) {
            if (message.getPerformative() == ACLMessage.PROPOSE) {
                recordProposal(message);
            }
        } else if (message.getPerformative() == ACLMessage.INFORM) {
            if ("TASK_STARTED".equals(type)) {
                JsonNode body = read(message.getContent());
                String robotId = body.path("robotId").asText();
                availableRobots.remove(robotLocalName(robotId));
                tasks.markInProgress(body.path("taskId").asText(), robotId);
            } else if ("TASK_COMPLETED".equals(type)) {
                completeTask(message);
            } else if ("TASK_FAILED".equals(type)) {
                failTask(message);
            } else if ("ROBOT_AVAILABLE".equals(type)) {
                JsonNode body = read(message.getContent());
                String robotId = body.path("robotId").asText();
                if (!robotId.isBlank()
                        && tasks.taskForRobot(robotId).isEmpty()) {
                    availableRobots.add(message.getSender().getLocalName());
                }
                startNextNegotiation();
            }
        }
    }

    private void enqueueTask(ACLMessage request) {
        try {
            JsonNode task = mapper.readTree(request.getContent());
            String taskId = task.path("taskId").asText();
            if (taskId.isBlank() || !tasks.enqueue(taskId, request.getContent())) {
                return;
            }
            startNextNegotiation();
        } catch (Exception exception) {
            LOGGER.warn("Invalid transport task request", exception);
        }
    }

    private void startNextNegotiation() {
        if (activeConversationId != null || !SimulationEventBus.isRunning()) {
            return;
        }
        TaskQueue.Entry task = tasks.announceNext().orElse(null);
        if (task == null) {
            return;
        }
        DFAgentDescription[] robots =
                AgentService.search(this, MessageProtocol.ROBOT_SERVICE);
        Set<String> selectedRobots = new HashSet<>();
        for (DFAgentDescription robot : robots) {
            String localName = robot.getName().getLocalName();
            if (availableRobots.remove(localName)) {
                selectedRobots.add(localName);
            }
        }
        if (selectedRobots.isEmpty()) {
            tasks.requeue(task.id());
            return;
        }

        activeTaskId = task.id();
        activeConversationId = task.id();
        activeTaskContent = task.content();
        proposals.clear();
        SimulationEventBus.negotiationStarted();
        for (DFAgentDescription robot : robots) {
            String robotName = robot.getName().getLocalName();
            if (!selectedRobots.contains(robotName)) {
                continue;
            }
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.addReceiver(robot.getName());
            cfp.setConversationId(activeConversationId);
            cfp.setLanguage("JSON");
            cfp.setContent(activeTaskContent);
            send(cfp);
            SimulationEventBus.publishLog("ALLOCATION-AGENT", robotName,
                    "CFP", "TASK " + activeTaskId);
        }
        negotiationTimerScheduled = true;
        addBehaviour(new WakerBehaviour(this, 900) {
            @Override
            protected void handleElapsedTimeout() {
                finishNegotiation(activeConversationId);
            }
        });
    }

    private void recordProposal(ACLMessage message) {
        try {
            JsonNode body = mapper.readTree(message.getContent());
            String robotId = body.path("robotId").asText();
            TaskProposal proposal = new TaskProposal(robotId,
                    body.path("distance").asDouble(),
                    body.path("estimatedTime").asDouble(),
                    body.path("batteryCost").asDouble(),
                    body.path("workload").asDouble());
            proposal.setScore(scoring.calculate(proposal,
                    body.path("batteryLevel").asDouble(), true));
            proposals.put(robotId, proposal);
            SimulationEventBus.publishLog(robotId, "ALLOCATION-AGENT",
                    "PROPOSE", "COST=" + Math.round(proposal.getEstimatedTime()));
        } catch (Exception exception) {
            LOGGER.warn("Invalid robot proposal", exception);
        }
    }

    private void finishNegotiation(String conversationId) {
        if (!negotiationTimerScheduled || !conversationId.equals(activeConversationId)) {
            return;
        }
        if (!SimulationEventBus.isRunning()) {
            addBehaviour(new WakerBehaviour(this, 250) {
                @Override
                protected void handleElapsedTimeout() {
                    finishNegotiation(conversationId);
                }
            });
            return;
        }
        negotiationTimerScheduled = false;
        TaskProposal best = proposals.values().stream()
                .filter(proposal -> proposal.getScore() >= 0)
                .max((left, right) -> Double.compare(left.getScore(), right.getScore()))
                .orElse(null);
        DFAgentDescription[] robots =
                AgentService.search(this, MessageProtocol.ROBOT_SERVICE);
        if (best == null) {
            for (String robotId : proposals.keySet()) {
                sendDecision(new AID(robotLocalName(robotId), AID.ISLOCALNAME),
                        ACLMessage.REJECT_PROPOSAL,
                        activeConversationId, "{\"type\":\"NO_SUITABLE_ROBOT\"}");
            }
            String failedTaskId = activeTaskId;
            tasks.fail(failedTaskId, null);
            notifyOrderFailed(failedTaskId, "NO_SUITABLE_ROBOT");
            clearNegotiation();
            SimulationEventBus.negotiationFinished();
            scheduleQueuePump();
            return;
        }

        String winner = robotLocalName(best.getRobotId());
        if (!tasks.assign(activeTaskId, best.getRobotId())) {
            LOGGER.warn("Task already assigned or robot has another task: {}", activeTaskId);
            String failedTaskId = activeTaskId;
            tasks.fail(failedTaskId, null);
            notifyOrderFailed(failedTaskId, "DUPLICATE_ASSIGNMENT");
            clearNegotiation();
            SimulationEventBus.negotiationFinished();
            scheduleQueuePump();
            return;
        }
        availableRobots.remove(winner);
        for (DFAgentDescription robot : robots) {
            boolean accepted = robot.getName().getLocalName().equalsIgnoreCase(winner);
            String candidateId = "ROBOT-" + robot.getName().getLocalName()
                    .replaceFirst("(?i)^robot-", "");
            if (!accepted && !proposals.containsKey(candidateId)) {
                continue;
            }
            sendDecision(robot.getName(), accepted
                            ? ACLMessage.ACCEPT_PROPOSAL : ACLMessage.REJECT_PROPOSAL,
                    activeConversationId,
                    accepted ? activeTaskContent : "{\"type\":\"TASK_REJECTED\"}");
            SimulationEventBus.publishLog("ALLOCATION-AGENT",
                    robot.getName().getLocalName(),
                    accepted ? "ACCEPT_PROPOSAL" : "REJECT_PROPOSAL",
                    accepted ? "ASSIGNED " + activeTaskId : "DECLINED");
        }
        clearNegotiation();
        SimulationEventBus.negotiationFinished();
        scheduleQueuePump();
    }

    private void sendDecision(AID receiver, int performative,
                              String conversationId, String content) {
        ACLMessage decision = new ACLMessage(performative);
        decision.addReceiver(receiver);
        decision.setConversationId(conversationId);
        decision.setLanguage("JSON");
        decision.setContent(content);
        send(decision);
    }

    private void completeTask(ACLMessage message) {
        JsonNode body = read(message.getContent());
        String taskId = body.path("taskId").asText();
        String robotId = body.path("robotId").asText();
        if (!tasks.complete(taskId, robotId)) {
            return;
        }
        availableRobots.remove(robotLocalName(robotId));
        SimulationEventBus.publishLog(robotId, "ALLOCATION-AGENT",
                "INFORM", "TASK_COMPLETED " + taskId);
        startNextNegotiation();
    }

    private void failTask(ACLMessage message) {
        JsonNode body = read(message.getContent());
        String taskId = body.path("taskId").asText();
        String robotId = body.path("robotId").asText();
        if (tasks.fail(taskId, robotId)) {
            notifyOrderFailed(taskId, body.path("reason").asText("ROBOT_FAILURE"));
        }
        startNextNegotiation();
    }

    private void notifyOrderFailed(String taskId, String reason) {
        JsonNode body = read(tasks.get(taskId).map(TaskQueue.Entry::content).orElse("{}"));
        ACLMessage failure = new ACLMessage(ACLMessage.INFORM);
        failure.addReceiver(new AID("order-agent", AID.ISLOCALNAME));
        failure.setConversationId(taskId);
        failure.setLanguage("JSON");
        failure.setContent("{\"type\":\"TASK_FAILED\",\"taskId\":\"" + taskId
                + "\",\"orderId\":\"" + body.path("orderId").asText()
                + "\",\"reason\":\"" + reason + "\"}");
        send(failure);
        SimulationEventBus.incrementFailedTasks();
    }

    private JsonNode read(String content) {
        try {
            return mapper.readTree(content);
        } catch (Exception exception) {
            return mapper.createObjectNode();
        }
    }

    private String robotLocalName(String robotId) {
        String suffix = robotId.toLowerCase().replace("robot-", "");
        return "robot-" + suffix;
    }

    private String extractType(String content) {
        return read(content).path("type").asText();
    }

    private void clearNegotiation() {
        activeTaskId = null;
        activeConversationId = null;
        activeTaskContent = null;
        proposals.clear();
    }

    private void scheduleQueuePump() {
        addBehaviour(new WakerBehaviour(this, 250) {
            @Override
            protected void handleElapsedTimeout() {
                startNextNegotiation();
            }
        });
    }

    @Override
    protected void takeDown() {
        AgentService.deregister(this);
    }
}
