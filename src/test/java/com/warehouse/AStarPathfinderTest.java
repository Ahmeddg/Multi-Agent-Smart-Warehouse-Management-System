package com.warehouse;

import com.warehouse.algorithms.AStarPathfinder;
import com.warehouse.environment.CellType;
import com.warehouse.environment.Position;
import com.warehouse.environment.WarehouseGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AStarPathfinderTest {
    @Test
    void findsShortestManhattanRoute() {
        WarehouseGrid grid = new WarehouseGrid(5, 5);
        AStarPathfinder pathfinder = new AStarPathfinder();

        List<Position> path = pathfinder.findPath(
                grid,
                new Position(0, 0),
                new Position(2, 2));

        assertEquals(5, path.size());
        assertEquals(new Position(0, 0), path.get(0));
        assertEquals(new Position(2, 2), path.get(path.size() - 1));
    }

    @Test
    void recalculatesAroundObstacle() {
        WarehouseGrid grid = new WarehouseGrid(5, 5);
        grid.setCellType(new Position(1, 0), CellType.OBSTACLE);
        grid.setCellType(new Position(1, 1), CellType.SHELF);
        AStarPathfinder pathfinder = new AStarPathfinder();

        List<Position> path = pathfinder.findPath(
                grid,
                new Position(0, 0),
                new Position(2, 0));

        assertFalse(path.isEmpty());
        assertEquals(new Position(2, 0), path.get(path.size() - 1));
    }

    @Test
    void reportsNoRouteForBlockedDestination() {
        WarehouseGrid grid = new WarehouseGrid(3, 3);
        grid.setCellType(new Position(1, 1), CellType.OBSTACLE);
        grid.setCellType(new Position(1, 2), CellType.OBSTACLE);
        grid.setCellType(new Position(2, 1), CellType.OBSTACLE);
        grid.setCellType(new Position(2, 2), CellType.OBSTACLE);
        AStarPathfinder pathfinder = new AStarPathfinder();

        List<Position> path = pathfinder.findPath(
                grid,
                new Position(0, 0),
                new Position(2, 2));

        assertTrue(path.isEmpty());
    }

    @Test
    void routesAroundOccupiedRobotCell() {
        WarehouseGrid grid = new WarehouseGrid(5, 3);
        grid.occupy(new Position(1, 1), "ROBOT-01");
        AStarPathfinder pathfinder = new AStarPathfinder();

        List<Position> path = pathfinder.findPath(
                grid,
                new Position(0, 1),
                new Position(2, 1));

        assertFalse(path.isEmpty());
        assertFalse(path.contains(new Position(1, 1)));
        assertEquals(new Position(2, 1), path.get(path.size() - 1));
    }
}
