package com.warehouse.agents;

import com.warehouse.simulation.SimulationEventBus;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

public class ScenarioDriverAgent extends Agent {
    private int sent;

    @Override
    protected void setup() {
        Object[] arguments = getArguments();
        int orderCount = arguments != null && arguments.length > 1
                ? Integer.parseInt(String.valueOf(arguments[1])) : 1;
        int speed = arguments != null && arguments.length > 2
                ? Integer.parseInt(String.valueOf(arguments[2])) : 100;
        int interval = Math.max(250, 1800 - (speed * 12));

        addBehaviour(new TickerBehaviour(this, interval) {
            @Override
            protected void onTick() {
                if (!SimulationEventBus.isRunning()) {
                    return;
                }
                if (sent >= orderCount) {
                    stop();
                    return;
                }
                sent++;
                String orderId = "T" + String.format("%02d", sent);
                int productIndex = (sent - 1) % 3;
                String productId = "P" + (productIndex + 1);
                int[][] pickups = {{2, 2}, {17, 2}, {6, 2}};
                int[][] deliveries = {
                        {17, 17}, {2, 17}, {10, 17},
                        {2, 12}, {6, 12}, {10, 12}, {14, 12}, {18, 12},
                        {2, 14}, {6, 14}, {10, 14}, {14, 14}, {18, 14},
                        {2, 16}, {6, 16}, {10, 16}, {14, 16}, {18, 16},
                        {6, 18}, {14, 18}
                };
                int[] pickup = pickups[productIndex];
                int[] delivery = deliveries[sent - 1];
                String content = "{\"type\":\"NEW_ORDER\",\"orderId\":\"" + orderId
                        + "\",\"productId\":\"" + productId + "\",\"quantity\":1"
                        + ",\"pickup\":{\"x\":" + pickup[0] + ",\"y\":" + pickup[1] + "}"
                        + ",\"delivery\":{\"x\":" + delivery[0] + ",\"y\":" + delivery[1] + "}}";

                ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                request.addReceiver(new AID("order-agent", AID.ISLOCALNAME));
                request.setConversationId(orderId);
                request.setLanguage("JSON");
                request.setContent(content);
                send(request);
                SimulationEventBus.orderGenerated();
                SimulationEventBus.publishLog("SCENARIO-DRIVER", "ORDER-AGENT",
                        "REQUEST", "NEW_ORDER " + orderId);
            }
        });
    }
}
