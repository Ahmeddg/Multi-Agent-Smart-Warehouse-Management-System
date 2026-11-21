package com.warehouse.simulation;

import com.warehouse.environment.CellType;
import com.warehouse.environment.Position;
import com.warehouse.environment.Warehouse;
import com.warehouse.environment.WarehouseGrid;

public final class ScenarioManager {
    public enum Scenario {
        BASIC_ORDER,
        MULTI_ROBOT_NEGOTIATION,
        COLLISION_AVOIDANCE,
        DEADLOCK_RECOVERY,
        DYNAMIC_OBSTACLE,
        BATTERY_MANAGEMENT,
        FULL_WAREHOUSE_STRESS
    }

    public Warehouse createScenario(Scenario scenario) {
        WarehouseGrid grid = new WarehouseGrid(20, 20);
        Warehouse warehouse = new Warehouse(grid);
        warehouse.registerPickupLocation("PICKUP-A", new Position(2, 2));
        warehouse.registerPickupLocation("PICKUP-B", new Position(17, 2));
        warehouse.registerPickupLocation("PICKUP-C", new Position(6, 2));
        warehouse.registerDeliveryLocation("DELIVERY-A", new Position(2, 17));
        warehouse.registerDeliveryLocation("DELIVERY-B", new Position(17, 17));
        warehouse.registerDeliveryLocation("DELIVERY-C", new Position(10, 17));
        int[][] additionalDeliveries = {
                {2, 12}, {6, 12}, {10, 12}, {14, 12}, {18, 12},
                {2, 14}, {6, 14}, {10, 14}, {14, 14}, {18, 14},
                {2, 16}, {6, 16}, {10, 16}, {14, 16}, {18, 16},
                {6, 18}, {14, 18}
        };
        for (int i = 0; i < additionalDeliveries.length; i++) {
            warehouse.registerDeliveryLocation("DELIVERY-" + (i + 4),
                    new Position(additionalDeliveries[i][0], additionalDeliveries[i][1]));
        }
        warehouse.registerChargingStation(new Position(10, 10));

        for (int x = 7; x <= 12; x++) {
            grid.setCellType(new Position(x, 6), CellType.SHELF);
        }
        switch (scenario) {
            case COLLISION_AVOIDANCE, DYNAMIC_OBSTACLE ->
                    grid.setTemporaryObstacle(new Position(10, 10), true);
            case DEADLOCK_RECOVERY -> {
                grid.setCellType(new Position(9, 9), CellType.OBSTACLE);
                grid.setCellType(new Position(10, 9), CellType.OBSTACLE);
            }
            case BATTERY_MANAGEMENT ->
                    warehouse.registerChargingStation(new Position(18, 18));
            case FULL_WAREHOUSE_STRESS -> {
                warehouse.registerChargingStation(new Position(18, 18));
                grid.setTemporaryObstacle(new Position(10, 10), true);
                grid.setCellType(new Position(5, 12), CellType.OBSTACLE);
            }
            default -> { }
        }
        return warehouse;
    }
}
