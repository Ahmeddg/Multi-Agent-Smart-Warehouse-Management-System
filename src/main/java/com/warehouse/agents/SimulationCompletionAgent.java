package com.warehouse.agents;

import com.warehouse.simulation.SimulationEventBus;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;

public class SimulationCompletionAgent extends Agent {
    @Override
    protected void setup() {
        addBehaviour(new TickerBehaviour(this, 500) {
            @Override
            protected void onTick() {
                SimulationEventBus.finishSimulationIfComplete();
            }
        });
    }
}
