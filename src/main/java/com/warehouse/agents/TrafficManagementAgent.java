package com.warehouse.agents;

import com.warehouse.communication.AgentService;
import com.warehouse.communication.MessageProtocol;
import com.warehouse.environment.Position;
import com.warehouse.environment.Warehouse;
import com.warehouse.simulation.SimulationEventBus;
import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class TrafficManagementAgent extends Agent {
    private final java.util.Map<String, String> reservedCells =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, java.util.Set<AID>> waitingByCell =
            new java.util.concurrent.ConcurrentHashMap<>();
    private Warehouse warehouse;

    @Override
    protected void setup() {
        Object[] arguments = getArguments();
        if (arguments != null && arguments.length > 0
                && arguments[0] instanceof Warehouse value) {
            warehouse = value;
        }
        AgentService.register(this,
                "traffic-management-agent",
                MessageProtocol.TRAFFIC_SERVICE);
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
        SimulationEventBus.setAgentState("traffic-agent", "Traffic", "ACTIVE",
                "Managing reservations");
    }

    private void handleMessage(ACLMessage message) {
        if (message.getPerformative() != ACLMessage.REQUEST) {
            return;
        }
        if (message.getContent() != null
                && message.getContent().contains("\"type\":\"MOVEMENT_RELEASE\"")) {
            String robotId = message.getSender().getLocalName();
            String cell = extractRequestedCell(message.getContent());
            boolean released = false;
            Position position = parsePosition(cell);
            synchronized (reservedCells) {
                String owner = reservedCells.get(cell);
                if (robotId.equals(owner)) {
                    reservedCells.remove(cell);
                    released = true;
                } else if (owner == null && (warehouse == null
                        || warehouse.getGrid().getOccupant(position) == null)) {
                    released = true;
                }
            }
            if (!released) {
                return;
            }
            SimulationEventBus.releaseCell(position, robotId);
            SimulationEventBus.publishLog(robotId, "TRAFFIC-AGENT",
                    "INFORM", "CELL_RELEASED " + cell);
            java.util.Set<AID> waiters = waitingByCell.remove(cell);
            if (waiters != null) {
                for (AID waiter : waiters) {
                    ACLMessage available = new ACLMessage(ACLMessage.INFORM);
                    available.addReceiver(waiter);
                    available.setContent("{\"type\":\"CELL_AVAILABLE\"}");
                    send(available);
                }
            }
            return;
        }
        ACLMessage response = message.createReply();
        response.setLanguage("JSON");
        if (message.getContent() != null
                && message.getContent().contains("\"type\":\"MOVEMENT_REQUEST\"")) {
            String robotId = message.getSender().getLocalName();
            String cell = extractRequestedCell(message.getContent());
            synchronized (reservedCells) {
                String owner = reservedCells.get(cell);
                Position requested = parsePosition(cell);
                boolean traversable = warehouse == null
                        || warehouse.getGrid().canOccupy(requested);
                if ((owner == null || owner.equals(robotId)) && traversable) {
                    reservedCells.put(cell, robotId);
                    SimulationEventBus.reserveCell(parsePosition(cell), robotId);
                    response.setPerformative(ACLMessage.AGREE);
                    response.setContent("{\"type\":\"MOVEMENT_GRANTED\"}");
                    SimulationEventBus.publishLog(robotId, "TRAFFIC-AGENT",
                            "REQUEST", "CELL_RESERVATION " + cell);
                    SimulationEventBus.publishLog("TRAFFIC-AGENT", robotId,
                            "INFORM", "CELL_GRANTED " + cell);
                } else {
                    waitingByCell.computeIfAbsent(cell,
                            ignored -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                            .add(message.getSender());
                    response.setPerformative(ACLMessage.REFUSE);
                    response.setContent("{\"type\":\"CELL_OCCUPIED\"}");
                    SimulationEventBus.publishLog(robotId, "TRAFFIC-AGENT",
                            "REQUEST", "CELL_RESERVATION " + cell);
                    SimulationEventBus.publishLog("TRAFFIC-AGENT", robotId,
                            "REFUSE", "CELL_OCCUPIED " + cell);
                }

            }
            send(response);
        }
    }

    private Position parsePosition(String cell) {
        String[] parts = cell.split(",");
        return new Position(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private String extractRequestedCell(String content) {
        return extractJsonInt(content, "\"x\"") + ","
                + extractJsonInt(content, "\"y\"");
    }

    private int extractJsonInt(String content, String field) {
        int fieldIndex = content.indexOf(field);
        int colon = content.indexOf(':', fieldIndex);
        int comma = content.indexOf(',', colon);
        int end = comma < 0 ? content.indexOf('}', colon) : comma;
        return Integer.parseInt(content.substring(colon + 1, end).trim());
    }

    @Override
    protected void takeDown() {
        AgentService.deregister(this);
    }
}
