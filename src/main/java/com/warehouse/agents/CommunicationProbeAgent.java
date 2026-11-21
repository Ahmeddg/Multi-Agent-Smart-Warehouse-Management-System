package com.warehouse.agents;

import com.warehouse.communication.AgentService;
import com.warehouse.communication.MessageProtocol;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;

import java.util.UUID;

public class CommunicationProbeAgent extends Agent {
    private final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(CommunicationProbeAgent.class);

    @Override
    protected void setup() {
        addBehaviour(new WakerBehaviour(this, 750) {
            @Override
            protected void onWake() {
                DFAgentDescription[] inventories =
                        AgentService.search(myAgent, MessageProtocol.INVENTORY_SERVICE);
                if (inventories.length == 0) {
                    logger.warn("Communication probe could not discover inventory service");
                    return;
                }

                AID inventory = inventories[0].getName();
                ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
                message.addReceiver(inventory);
                message.setConversationId("phase2-" + UUID.randomUUID());
                message.setLanguage("JSON");
                message.setContent("{\"type\":\"STATUS_REQUEST\"}");
                myAgent.send(message);
                logger.info("Probe sent STATUS_REQUEST to {}", inventory.getLocalName());
            }
        });

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage message = myAgent.receive();
                if (message == null) {
                    block();
                    return;
                }
                logger.info("Probe received {} from {}: {}",
                        ACLMessage.getPerformative(message.getPerformative()),
                        message.getSender().getLocalName(),
                        message.getContent());
            }
        });
    }

    @Override
    protected void takeDown() {
        AgentService.deregister(this);
    }
}
