package com.warehouse.environment;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Warehouse {
    private final WarehouseGrid grid;
    private final Map<String, Position> pickupLocations = new HashMap<>();
    private final Map<String, Position> deliveryLocations = new HashMap<>();

    public Warehouse(WarehouseGrid grid) {
        this.grid = grid;
    }

    public WarehouseGrid getGrid() {
        return grid;
    }

    public void registerPickupLocation(String id, Position position) {
        validatePosition(position);
        pickupLocations.put(id, position);
        grid.setCellType(position, CellType.PICKUP);
    }

    public void registerDeliveryLocation(String id, Position position) {
        validatePosition(position);
        deliveryLocations.put(id, position);
        grid.setCellType(position, CellType.DELIVERY);
    }

    public void registerChargingStation(Position position) {
        validatePosition(position);
        grid.setCellType(position, CellType.CHARGING_STATION);
    }

    public Map<String, Position> getPickupLocations() {
        return Collections.unmodifiableMap(pickupLocations);
    }

    public Map<String, Position> getDeliveryLocations() {
        return Collections.unmodifiableMap(deliveryLocations);
    }

    private void validatePosition(Position position) {
        if (!grid.contains(position)) {
            throw new IllegalArgumentException("Location outside warehouse: " + position);
        }
    }
}
