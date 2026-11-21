package com.warehouse.ui;

import com.warehouse.environment.CellType;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public final class WarehouseVisuals {
    private static final List<Color> ROBOT_COLORS = List.of(
            Color.CRIMSON, Color.BLUE, Color.DARKVIOLET,
            Color.DARKORANGE, Color.DARKCYAN, Color.DEEPPINK);

    private WarehouseVisuals() {
    }

    public static Color cellColor(CellType type, boolean dynamicObstacle) {
        if (dynamicObstacle) {
            return Color.PURPLE;
        }
        return switch (type) {
            case SHELF -> Color.DARKGRAY;
            case OBSTACLE -> Color.BLACK;
            case PICKUP -> Color.LIMEGREEN;
            case DELIVERY -> Color.ORANGE;
            case CHARGING_STATION -> Color.DODGERBLUE;
            case ROBOT, EMPTY -> Color.LIGHTGRAY;
        };
    }

    public static Color robotColor(String robotId) {
        String digits = robotId.replaceAll("\\D+", "");
        int index;
        try {
            index = Integer.parseInt(digits) - 1;
        } catch (NumberFormatException exception) {
            index = Math.floorMod(robotId.hashCode(), ROBOT_COLORS.size());
        }
        return ROBOT_COLORS.get(Math.floorMod(index, ROBOT_COLORS.size()));
    }

    public static List<LegendEntry> legend(int robotCount) {
        List<LegendEntry> entries = new ArrayList<>(List.of(
                new LegendEntry("Empty cell", Color.LIGHTGRAY, "cell"),
                new LegendEntry("Storage shelf", Color.DARKGRAY, "cell"),
                new LegendEntry("Static obstacle", Color.BLACK, "cell"),
                new LegendEntry("Dynamic obstacle", Color.PURPLE, "cell"),
                new LegendEntry("Product pickup", Color.LIMEGREEN, "cell"),
                new LegendEntry("Delivery station", Color.ORANGE, "cell"),
                new LegendEntry("Charging station", Color.DODGERBLUE, "cell"),
                new LegendEntry("Reserved cell", Color.YELLOW, "border"),
                new LegendEntry("Destination", Color.YELLOW, "ring")));
        for (int i = 1; i <= robotCount; i++) {
            String robotId = String.format("ROBOT-%02d", i);
            entries.add(new LegendEntry(robotId + " and planned path",
                    robotColor(robotId), "robot-line"));
        }
        entries.addAll(List.of(
                new LegendEntry("IDLE state", Color.WHITE, "state:I"),
                new LegendEntry("MOVING state", Color.WHITE, "state:>"),
                new LegendEntry("WAITING state", Color.WHITE, "state:!"),
                new LegendEntry("CHARGING state", Color.WHITE, "state:C"),
                new LegendEntry("NEGOTIATING state", Color.WHITE, "state:N"),
                new LegendEntry("PICKING_UP state", Color.WHITE, "state:P"),
                new LegendEntry("DELIVERING state", Color.WHITE, "state:D"),
                new LegendEntry("BLOCKED state", Color.WHITE, "state:X"),
                new LegendEntry("Carrying product", Color.WHITE, "box")));
        return List.copyOf(entries);
    }

    public record LegendEntry(String label, Color color, String marker) {
    }
}
