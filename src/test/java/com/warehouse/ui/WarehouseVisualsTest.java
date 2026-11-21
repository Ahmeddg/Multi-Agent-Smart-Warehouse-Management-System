package com.warehouse.ui;

import com.warehouse.environment.CellType;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseVisualsTest {
    @Test
    void legendUsesTheRendererCellColors() {
        assertEquals(Color.LIGHTGRAY,
                WarehouseVisuals.cellColor(CellType.EMPTY, false));
        assertEquals(Color.DARKGRAY,
                WarehouseVisuals.cellColor(CellType.SHELF, false));
        assertEquals(Color.BLACK,
                WarehouseVisuals.cellColor(CellType.OBSTACLE, false));
        assertEquals(Color.PURPLE,
                WarehouseVisuals.cellColor(CellType.EMPTY, true));
        assertEquals(Color.LIMEGREEN,
                WarehouseVisuals.cellColor(CellType.PICKUP, false));
        assertEquals(Color.ORANGE,
                WarehouseVisuals.cellColor(CellType.DELIVERY, false));
        assertEquals(Color.DODGERBLUE,
                WarehouseVisuals.cellColor(CellType.CHARGING_STATION, false));
    }

    @Test
    void robotColorsAreStableAndTheLegendTracksConfiguredRobots() {
        assertEquals(WarehouseVisuals.robotColor("ROBOT-01"),
                WarehouseVisuals.robotColor("ROBOT-01"));
        assertNotEquals(WarehouseVisuals.robotColor("ROBOT-01"),
                WarehouseVisuals.robotColor("ROBOT-02"));
        assertTrue(WarehouseVisuals.legend(4).stream()
                .anyMatch(entry -> entry.label().startsWith("ROBOT-04")));
        assertEquals(3, WarehouseVisuals.legend(3).stream()
                .filter(entry -> entry.label().startsWith("ROBOT-")).count());
    }
}
