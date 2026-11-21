package com.warehouse.agents;

import com.warehouse.communication.AgentService;
import com.warehouse.communication.MessageProtocol;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class WarehouseMonitoringAgent extends Agent {
    @Override
    protected void setup() {
        AgentService.register(this,
                "warehouse-monitor-agent",
                MessageProtocol.MONITORING_SERVICE);
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage message = myAgent.receive();
                if (message == null) {
                    block();
                }
            }
        });
    }

    @Override
    protected void takeDown() {
        AgentService.deregister(this);
    }
}
