package com.warehouse.algorithms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeadlockDetector {
    public List<String> detectCycle(Map<String, String> waitingFor) {
        Map<String, Integer> state = new HashMap<>();
        List<String> cycle = new ArrayList<>();
        for (String robot : waitingFor.keySet()) {
            if (state.getOrDefault(robot, 0) == 0
                    && findCycle(robot, waitingFor, state, new ArrayList<>(), cycle)) {
                break;
            }
        }
        return cycle;
    }

    private boolean findCycle(String robot,
                              Map<String, String> waitingFor,
                              Map<String, Integer> state,
                              List<String> path,
                              List<String> cycle) {
        state.put(robot, 1);
        path.add(robot);
        String blockedBy = waitingFor.get(robot);
        if (blockedBy != null) {
            int blockedState = state.getOrDefault(blockedBy, 0);
            if (blockedState == 1) {
                int start = path.indexOf(blockedBy);
                cycle.addAll(path.subList(start, path.size()));
                return true;
            }
            if (blockedState == 0
                    && findCycle(blockedBy, waitingFor, state, path, cycle)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        state.put(robot, 2);
        return false;
    }
}
