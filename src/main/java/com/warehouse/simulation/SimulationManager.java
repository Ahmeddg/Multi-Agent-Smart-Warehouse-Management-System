package com.warehouse.simulation;

import com.warehouse.environment.Position;
import com.warehouse.environment.Warehouse;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public final class SimulationManager {
    private final ScenarioManager scenarioManager = new ScenarioManager();
    private ContainerController container;
    private Warehouse warehouse;
    private ScenarioManager.Scenario scenario;
    private int robotCount = 2;
    private int orderCount = 1;
    private int speedPercent = 100;

    public synchronized void start(ScenarioManager.Scenario selectedScenario,
                                   int selectedRobotCount,
                                   int selectedOrderCount,
                                   int selectedSpeedPercent) throws Exception {
        if (container != null) {
            reset();
        }
        this.scenario = selectedScenario;
        this.robotCount = Math.max(1, selectedRobotCount);
        this.orderCount = Math.max(1, selectedOrderCount);
        this.speedPercent = Math.max(10, selectedSpeedPercent);
        SimulationEventBus.reset();
        SimulationEventBus.configureWorkload(robotCount, orderCount);
        warehouse = scenarioManager.createScenario(scenario);

        Runtime runtime = Runtime.instance();
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.MAIN_PORT, "1099");
        profile.setParameter(Profile.LOCAL_HOST, "localhost");
        profile.setParameter(Profile.GUI, "false");
        container = runtime.createMainContainer(profile);

        createAgent("order-agent", "com.warehouse.agents.OrderManagementAgent");
        createAgent("inventory-agent", "com.warehouse.agents.InventoryAgent");
        createAgent("allocation-agent", "com.warehouse.agents.TaskAllocationAgent");
        createAgent("traffic-agent", "com.warehouse.agents.TrafficManagementAgent",
                warehouse);
        createAgent("warehouse-monitor", "com.warehouse.agents.WarehouseMonitoringAgent");
        createAgent("simulation-completion", "com.warehouse.agents.SimulationCompletionAgent");

        Position[] starts = {
                new Position(1, 1), new Position(3, 1), new Position(1, 3),
                new Position(3, 3), new Position(15, 1), new Position(15, 3)
        };
        for (int i = 0; i < robotCount; i++) {
            String id = String.format("ROBOT-%02d", i + 1);
            String name = "robot-" + String.format("%02d", i + 1);
            double battery = 55.0 + (i * 12.0) % 45.0;
            createAgent(name, "com.warehouse.agents.RobotAgent",
                    id, warehouse, starts[i % starts.length], battery, speedPercent);
        }

        generateOrders();
        SimulationEventBus.setStatus("RUNNING");
    }

    private void createAgent(String name, String className, Object... arguments) throws Exception {
        AgentController agent = container.createNewAgent(name, className, arguments);
        agent.start();
    }

    private void generateOrders() {
        try {
            createAgent("scenario-driver",
                    "com.warehouse.agents.ScenarioDriverAgent",
                    scenario.name(), orderCount, speedPercent);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to start scenario driver", exception);
        }
    }

    public void pause() {
        SimulationEventBus.setStatus("PAUSED");
    }

    public void resume() {
        if ("PAUSED".equals(SimulationEventBus.getStatus())) {
            SimulationEventBus.setStatus("RUNNING");
        }
    }

    public synchronized void reset() throws Exception {
        if (container != null) {
            try {
                container.kill();
            } catch (Exception ignored) {
                // A killed container is already detached.
            }
        }
        container = null;
        warehouse = null;
        SimulationEventBus.reset();
        SimulationEventBus.setStatus("RESET");
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public ScenarioManager.Scenario getScenario() {
        return scenario;
    }
}
