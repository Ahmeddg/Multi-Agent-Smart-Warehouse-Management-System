package com.warehouse.communication;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.util.Objects;

public final class AgentService {
    private AgentService() {
    }

    public static void register(Agent agent, String serviceName, String serviceType) {
        Objects.requireNonNull(agent);

        DFAgentDescription description = new DFAgentDescription();
        description.setName(agent.getAID());

        ServiceDescription service = new ServiceDescription();
        service.setName(serviceName);
        service.setType(serviceType);
        description.addServices(service);

        try {
            DFService.register(agent, description);
        } catch (FIPAException exception) {
            throw new IllegalStateException(
                    "Unable to register agent service " + serviceName,
                    exception);
        }
    }

    public static DFAgentDescription[] search(
            Agent agent,
            String serviceType) {
        Objects.requireNonNull(agent);
        Objects.requireNonNull(serviceType);

        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription service = new ServiceDescription();
        service.setType(serviceType);
        template.addServices(service);

        try {
            return DFService.search(agent, template);
        } catch (FIPAException exception) {
            throw new IllegalStateException(
                    "Unable to search service " + serviceType,
                    exception);
        }
    }

    public static void deregister(Agent agent) {
        try {
            DFService.deregister(agent);
        } catch (FIPAException ignored) {
            // Agent shutdown should not fail because of an optional DF deregistration.
        }
    }
}
