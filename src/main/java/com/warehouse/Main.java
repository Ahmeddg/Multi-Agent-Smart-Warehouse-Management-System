package com.warehouse;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        Runtime runtime = Runtime.instance();
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.MAIN_PORT, "1099");
        profile.setParameter(Profile.LOCAL_HOST, "localhost");
        profile.setParameter(Profile.GUI, "false");

        ContainerController container = runtime.createMainContainer(profile);
        try {
            startAgents(container);
            runCommunicationVerification(container);
        } catch (Exception exception) {
            LOGGER.error("Unable to start warehouse JADE container", exception);
            System.exit(1);
        }
    }

    private static void startAgents(ContainerController container) throws Exception {
        createAgent(container, "order-management", "com.warehouse.agents.OrderManagementAgent");
        createAgent(container, "inventory", "com.warehouse.agents.InventoryAgent");
        createAgent(container, "task-allocation", "com.warehouse.agents.TaskAllocationAgent");
        createAgent(container, "traffic-management", "com.warehouse.agents.TrafficManagementAgent");
        createAgent(container, "warehouse-monitor", "com.warehouse.agents.WarehouseMonitoringAgent");
        createAgent(container, "robot-01", "com.warehouse.agents.RobotAgent", "ROBOT-01");
        createAgent(container, "robot-02", "com.warehouse.agents.RobotAgent", "ROBOT-02");
    }

    private static void createAgent(ContainerController container,
                                    String name,
                                    String className,
                                    Object... arguments) throws Exception {
        AgentController agent = container.createNewAgent(name, className, arguments);
        agent.start();
        LOGGER.info("Started JADE agent {}", name);
    }

    private static void runCommunicationVerification(ContainerController container) throws Exception {
        createAgent(container,
                "phase2-probe",
                "com.warehouse.agents.CommunicationProbeAgent");
    }
}
