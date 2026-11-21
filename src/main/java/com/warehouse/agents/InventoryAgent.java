package com.warehouse.agents;

import com.warehouse.communication.AgentService;
import com.warehouse.communication.MessageProtocol;
import com.warehouse.environment.Position;
import com.warehouse.models.Product;
import com.warehouse.simulation.StockInventory;
import com.warehouse.simulation.SimulationEventBus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class InventoryAgent extends Agent {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StockInventory inventory = new StockInventory();
    private final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(InventoryAgent.class);

    @Override
    protected void setup() {
        inventory.addStock(new Product("P1", "Packaging Box", new Position(2, 2)), 20);
        inventory.addStock(new Product("P2", "Electronic Module", new Position(17, 2)), 12);
        inventory.addStock(new Product("P3", "Safety Equipment", new Position(6, 2)), 8);
        AgentService.register(this,
                "inventory-agent",
                MessageProtocol.INVENTORY_SERVICE);
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage message = myAgent.receive();
                if (message == null) {
                    block();
                    return;
                }

                handleRequest(message);
            }
        });
        SimulationEventBus.setAgentState("inventory-agent", "Inventory", "ACTIVE",
                "Managing stock");
    }

    private void handleRequest(ACLMessage message) {
        if (message.getPerformative() != ACLMessage.REQUEST) {
            return;
        }
        try {
            JsonNode request = objectMapper.readTree(message.getContent());
            String type = request.path("type").asText();
            ACLMessage response = message.createReply();
            response.setLanguage("JSON");

            if ("STATUS_REQUEST".equals(type)) {
                response.setPerformative(ACLMessage.INFORM);
                response.setContent("{\"type\":\"AGENT_READY\",\"service\":\"inventory\"}");
            } else if ("STOCK_REQUEST".equals(type)) {
                String productId = request.path("productId").asText();
                int quantity = request.path("quantity").asInt();
                int available = inventory.getAvailableQuantity(productId);
                response.setPerformative(available >= quantity
                        ? ACLMessage.AGREE : ACLMessage.REFUSE);
                response.setContent("{\"type\":\"STOCK_RESULT\",\"productId\":\""
                        + productId + "\",\"available\":" + available + "}");
            } else if ("RESERVE_REQUEST".equals(type)) {
                String productId = request.path("productId").asText();
                int quantity = request.path("quantity").asInt();
                String orderId = request.path("orderId").asText();
                boolean reserved = inventory.reserve(productId, quantity, orderId);
                response.setPerformative(reserved ? ACLMessage.INFORM : ACLMessage.REFUSE);
                response.setContent("{\"type\":\"RESERVATION_RESULT\",\"productId\":\""
                        + productId + "\",\"orderId\":\"" + orderId
                        + "\",\"reserved\":" + reserved + "}");
                SimulationEventBus.publishLog("INVENTORY-AGENT", "ORDER-AGENT",
                        reserved ? "INFORM" : "REFUSE",
                        reserved ? "STOCK_RESERVED " + orderId : "RESERVATION_FAILED");
            } else if ("COMMIT_REQUEST".equals(type)) {
                String productId = request.path("productId").asText();
                int quantity = request.path("quantity").asInt();
                String orderId = request.path("orderId").asText();
                inventory.commitReservation(productId, quantity, orderId);
                response.setPerformative(ACLMessage.INFORM);
                response.setContent("{\"type\":\"COMMIT_CONFIRMED\",\"orderId\":\""
                        + orderId + "\"}");
            } else if ("RELEASE_REQUEST".equals(type)) {
                String productId = request.path("productId").asText();
                int quantity = request.path("quantity").asInt();
                String orderId = request.path("orderId").asText();
                inventory.releaseReservation(productId, quantity, orderId);
                response.setPerformative(ACLMessage.INFORM);
                response.setContent("{\"type\":\"RELEASE_CONFIRMED\",\"orderId\":\""
                        + orderId + "\"}");
            } else {
                response.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                response.setContent("{\"type\":\"UNKNOWN_REQUEST\"}");
            }
            send(response);
        } catch (Exception exception) {
            logger.warn("Rejected invalid inventory message", exception);
        }
    }

    @Override
    protected void takeDown() {
        AgentService.deregister(this);
    }
}
