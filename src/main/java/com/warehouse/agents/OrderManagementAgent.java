package com.warehouse.agents;

import com.warehouse.communication.AgentService;
import com.warehouse.communication.MessageProtocol;
import com.warehouse.simulation.SimulationEventBus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OrderManagementAgent extends Agent {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, JsonNode> pending = new ConcurrentHashMap<>();
    private final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(OrderManagementAgent.class);

    @Override
    protected void setup() {
        AgentService.register(this,
                "order-management-agent",
                MessageProtocol.ORDER_SERVICE);
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
        SimulationEventBus.setAgentState("order-agent", "Order", "ACTIVE",
                "Processing orders");
    }

    private void handleMessage(ACLMessage message) {
        try {
            JsonNode body = mapper.readTree(message.getContent());
            String type = body.path("type").asText();
            if (message.getPerformative() == ACLMessage.REQUEST
                    && "NEW_ORDER".equals(type)) {
                String orderId = body.path("orderId").asText();
                pending.put(orderId, body);
                SimulationEventBus.publishLog("ORDER-AGENT", "INVENTORY-AGENT",
                        "REQUEST", "STOCK_CHECK " + body.path("productId").asText());
                ACLMessage stock = new ACLMessage(ACLMessage.REQUEST);
                stock.addReceiver(new AID("inventory-agent", AID.ISLOCALNAME));
                stock.setConversationId(orderId);
                stock.setLanguage("JSON");
                stock.setContent("{\"type\":\"STOCK_REQUEST\",\"productId\":\""
                        + body.path("productId").asText()
                        + "\",\"quantity\":" + body.path("quantity").asInt() + "}");
                send(stock);
                SimulationEventBus.setAgentState("order-agent", "Order", "ACTIVE",
                        "Checking stock for " + orderId);
            } else if (message.getPerformative() == ACLMessage.AGREE
                    && "STOCK_RESULT".equals(type)) {
                reserveStock(message, body);
            } else if (message.getPerformative() == ACLMessage.INFORM
                    && "RESERVATION_RESULT".equals(type)
                    && body.path("reserved").asBoolean()) {
                createTransportTask(body);
            } else if (message.getPerformative() == ACLMessage.INFORM
                    && "TASK_COMPLETED".equals(type)) {
                String orderId = body.path("orderId").asText();
                commitStock(orderId);
            } else if (message.getPerformative() == ACLMessage.INFORM
                    && "TASK_FAILED".equals(type)) {
                failOrder(body.path("orderId").asText(),
                        body.path("reason").asText("TASK_FAILED"));
            } else if (message.getPerformative() == ACLMessage.REFUSE
                    && "STOCK_RESULT".equals(type)) {
                failOrder(message.getConversationId(), "INSUFFICIENT_STOCK");
            }
        } catch (Exception exception) {
            logger.warn("Unable to process order message", exception);
        }
    }

    private void reserveStock(ACLMessage message, JsonNode body) {
        String orderId = message.getConversationId();
        JsonNode order = pending.get(orderId);
        if (order == null) {
            return;
        }
        SimulationEventBus.publishLog("INVENTORY-AGENT", "ORDER-AGENT",
                "AGREE", "STOCK_AVAILABLE " + body.path("productId").asText());
        ACLMessage reserve = new ACLMessage(ACLMessage.REQUEST);
        reserve.addReceiver(new AID("inventory-agent", AID.ISLOCALNAME));
        reserve.setConversationId(orderId);
        reserve.setLanguage("JSON");
        reserve.setContent("{\"type\":\"RESERVE_REQUEST\",\"productId\":\""
                + order.path("productId").asText()
                + "\",\"quantity\":" + order.path("quantity").asInt()
                + ",\"orderId\":\"" + orderId + "\"}");
        send(reserve);
        SimulationEventBus.publishLog("ORDER-AGENT", "INVENTORY-AGENT",
                "REQUEST", "RESERVE_STOCK " + orderId);
    }

    private void createTransportTask(JsonNode body) {
        String orderId = body.path("orderId").asText();
        JsonNode order = pending.get(orderId);
        if (order == null) {
            return;
        }
        JsonNode pickup = order.path("pickup");
        JsonNode delivery = order.path("delivery");
        String taskId = "TASK-" + orderId;
        ACLMessage task = new ACLMessage(ACLMessage.REQUEST);
        task.addReceiver(new AID("allocation-agent", AID.ISLOCALNAME));
        task.setConversationId(taskId);
        task.setLanguage("JSON");
        task.setContent("{\"type\":\"TRANSPORT_TASK\",\"taskId\":\"" + taskId
                + "\",\"orderId\":\"" + orderId
                + "\",\"pickup\":{\"x\":" + pickup.path("x").asInt()
                + ",\"y\":" + pickup.path("y").asInt() + "}"
                + ",\"delivery\":{\"x\":" + delivery.path("x").asInt()
                + ",\"y\":" + delivery.path("y").asInt() + "}}");
        send(task);
        SimulationEventBus.publishLog("INVENTORY-AGENT", "ORDER-AGENT",
                "INFORM", "STOCK_RESERVED " + orderId);
        SimulationEventBus.publishLog("ORDER-AGENT", "ALLOCATION-AGENT",
                "REQUEST", "TRANSPORT_TASK " + taskId);
    }

    private void commitStock(String orderId) {
        JsonNode order = pending.remove(orderId);
        if (order == null) {
            return;
        }
        ACLMessage commit = new ACLMessage(ACLMessage.REQUEST);
        commit.addReceiver(new AID("inventory-agent", AID.ISLOCALNAME));
        commit.setConversationId(orderId);
        commit.setLanguage("JSON");
        commit.setContent("{\"type\":\"COMMIT_REQUEST\",\"productId\":\""
                + order.path("productId").asText() + "\",\"quantity\":1"
                + ",\"orderId\":\"" + orderId + "\"}");
        send(commit);
        SimulationEventBus.incrementCompletedOrders();
        SimulationEventBus.publishLog("ROBOT", "ORDER-AGENT",
                "INFORM", "TASK_COMPLETED " + orderId);
    }

    private void failOrder(String orderId, String reason) {
        JsonNode order = pending.remove(orderId);
        if (order == null) {
            return;
        }
        ACLMessage release = new ACLMessage(ACLMessage.REQUEST);
        release.addReceiver(new AID("inventory-agent", AID.ISLOCALNAME));
        release.setConversationId(orderId);
        release.setLanguage("JSON");
        release.setContent("{\"type\":\"RELEASE_REQUEST\",\"productId\":\""
                + order.path("productId").asText() + "\",\"quantity\":"
                + order.path("quantity").asInt() + ",\"orderId\":\"" + orderId + "\"}");
        send(release);
        SimulationEventBus.incrementFailedOrders();
        SimulationEventBus.publishLog("ORDER-AGENT", "SYSTEM",
                "FAILURE", orderId + " " + reason);
    }

    @Override
    protected void takeDown() {
        AgentService.deregister(this);
    }
}
