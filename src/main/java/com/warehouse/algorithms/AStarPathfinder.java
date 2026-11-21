package com.warehouse.algorithms;

import com.warehouse.environment.Position;
import com.warehouse.environment.WarehouseGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class AStarPathfinder {
    public List<Position> findPath(WarehouseGrid grid,
                                   Position start,
                                   Position destination) {
        if (!grid.contains(start) || !grid.contains(destination)) {
            return Collections.emptyList();
        }

        PriorityQueue<Node> open = new PriorityQueue<>(
                Comparator.comparingInt(Node::getEstimatedCost));
        Map<Position, Integer> costFromStart = new HashMap<>();
        Map<Position, Position> cameFrom = new HashMap<>();
        Set<Position> closed = new HashSet<>();

        costFromStart.put(start, 0);
        open.add(new Node(start, 0, start.manhattanDistance(destination)));

        while (!open.isEmpty()) {
            Node current = open.poll();
            if (closed.contains(current.position)) {
                continue;
            }
            if (current.position.equals(destination)) {
                return reconstructPath(cameFrom, destination);
            }
            closed.add(current.position);

            for (Position neighbor : neighbors(grid, current.position)) {
                if (closed.contains(neighbor)) {
                    continue;
                }
                int newCost = costFromStart.get(current.position) + 1;
                if (newCost < costFromStart.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    costFromStart.put(neighbor, newCost);
                    cameFrom.put(neighbor, current.position);
                    open.add(new Node(
                            neighbor,
                            newCost,
                            newCost + neighbor.manhattanDistance(destination)));
                }
            }
        }
        return Collections.emptyList();
    }

    private List<Position> neighbors(WarehouseGrid grid, Position position) {
        List<Position> neighbors = new ArrayList<>(4);
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            Position neighbor = new Position(
                    position.getX() + direction[0],
                    position.getY() + direction[1]);
            if (grid.isTraversable(neighbor)) {
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    private List<Position> reconstructPath(Map<Position, Position> cameFrom,
                                           Position destination) {
        List<Position> path = new ArrayList<>();
        Position current = destination;
        while (current != null) {
            path.add(current);
            current = cameFrom.get(current);
        }
        Collections.reverse(path);
        return path;
    }

    private static final class Node {
        private final Position position;
        private final int costFromStart;
        private final int estimatedCost;

        private Node(Position position, int costFromStart, int estimatedCost) {
            this.position = position;
            this.costFromStart = costFromStart;
            this.estimatedCost = estimatedCost;
        }

        private int getEstimatedCost() {
            return estimatedCost;
        }
    }
}
