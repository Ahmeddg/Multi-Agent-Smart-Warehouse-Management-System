package com.warehouse.environment;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class WarehouseGrid {
    private final int width;
    private final int height;
    private final Map<Position, Cell> cells = new HashMap<>();
    private final Map<Position, String> occupiedPositions = new HashMap<>();

    public WarehouseGrid(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Grid dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Position position = new Position(x, y);
                cells.put(position, new Cell(position, CellType.EMPTY));
            }
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean contains(Position position) {
        return position != null
                && position.getX() >= 0
                && position.getX() < width
                && position.getY() >= 0
                && position.getY() < height;
    }

    public Cell getCell(Position position) {
        if (!contains(position)) {
            throw new IllegalArgumentException("Position outside warehouse grid: " + position);
        }
        return cells.get(position);
    }

    public synchronized boolean isTraversable(Position position) {
        if (!contains(position)) {
            return false;
        }
        Cell cell = getCell(position);
        return cell.isTraversable() && !occupiedPositions.containsKey(position);
    }

    public void setCellType(Position position, CellType type) {
        getCell(position).setType(Objects.requireNonNull(type));
    }

    public void setTemporaryObstacle(Position position, boolean blocked) {
        getCell(position).setTemporarilyBlocked(blocked);
    }

    public synchronized boolean canOccupy(Position position) {
        return contains(position) && isTraversable(position);
    }

    public synchronized boolean occupy(Position position, String robotId) {
        Objects.requireNonNull(robotId);
        if (!canOccupy(position)) {
            return false;
        }
        occupiedPositions.put(position, robotId);
        return true;
    }

    public synchronized boolean moveRobot(String robotId, Position from, Position to) {
        Objects.requireNonNull(robotId);
        if (!occupiedPositions.containsKey(from)
                || !robotId.equals(occupiedPositions.get(from))) {
            return false;
        }
        if (!canOccupy(to)) {
            return false;
        }
        occupiedPositions.remove(from);
        occupiedPositions.put(to, robotId);
        return true;
    }

    public synchronized void release(Position position) {
        occupiedPositions.remove(position);
    }

    public synchronized String getOccupant(Position position) {
        return occupiedPositions.get(position);
    }
}
