package com.warehouse.environment;

import java.util.Objects;

public final class Cell {
    private final Position position;
    private CellType type;
    private boolean temporarilyBlocked;

    public Cell(Position position, CellType type) {
        this.position = Objects.requireNonNull(position);
        this.type = Objects.requireNonNull(type);
    }

    public Position getPosition() {
        return position;
    }

    public CellType getType() {
        return type;
    }

    public void setType(CellType type) {
        this.type = Objects.requireNonNull(type);
    }

    public boolean isTemporarilyBlocked() {
        return temporarilyBlocked;
    }

    public void setTemporarilyBlocked(boolean temporarilyBlocked) {
        this.temporarilyBlocked = temporarilyBlocked;
    }

    public boolean isTraversable() {
        return !temporarilyBlocked
                && type != CellType.SHELF
                && type != CellType.OBSTACLE;
    }
}
