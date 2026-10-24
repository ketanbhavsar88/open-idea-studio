package org.openideastudio.flow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, deterministic top-to-bottom layered layout for generated flows.
 * Users can move nodes afterwards; positions are then stored as edited.
 */
public final class FlowLayout {

    static final double X_GAP = 260;
    static final double Y_GAP = 140;

    private FlowLayout() {
    }

    /** Lays out every node, by longest-path-free BFS depth from start nodes. */
    public static List<FlowNode> layout(List<FlowNode> nodes, List<FlowEdge> edges) {
        Map<String, List<String>> next = new HashMap<>();
        edges.forEach(e -> next.computeIfAbsent(e.source(), k -> new ArrayList<>()).add(e.target()));

        Map<String, Integer> depth = new LinkedHashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        nodes.stream().filter(n -> n.type().equals("start")).forEach(n -> {
            depth.put(n.id(), 0);
            queue.add(n.id());
        });
        if (queue.isEmpty() && !nodes.isEmpty()) {
            depth.put(nodes.getFirst().id(), 0);
            queue.add(nodes.getFirst().id());
        }
        while (!queue.isEmpty()) {
            String id = queue.poll();
            for (String t : next.getOrDefault(id, List.of())) {
                if (!depth.containsKey(t)) {
                    depth.put(t, depth.get(id) + 1);
                    queue.add(t);
                }
            }
        }
        int maxDepth = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (FlowNode n : nodes) {
            depth.putIfAbsent(n.id(), ++maxDepth);
        }

        Map<Integer, Integer> rowCount = new HashMap<>();
        Map<Integer, Integer> rowSize = new HashMap<>();
        nodes.forEach(n -> rowSize.merge(depth.get(n.id()), 1, Integer::sum));
        List<FlowNode> placed = new ArrayList<>();
        for (FlowNode n : nodes) {
            int row = depth.get(n.id());
            int col = rowCount.merge(row, 1, Integer::sum) - 1;
            double x = (col - (rowSize.get(row) - 1) / 2.0) * X_GAP;
            placed.add(n.withPosition(new FlowNode.Position(x, row * Y_GAP)));
        }
        return placed;
    }

    /** A position for a node added next to {@code anchor} (or below everything when null). */
    public static FlowNode.Position near(FlowNode anchor, List<FlowNode> existing) {
        if (anchor != null) {
            double x = anchor.position().x();
            double y = anchor.position().y() + Y_GAP;
            while (occupied(existing, x, y)) {
                x += X_GAP;
            }
            return new FlowNode.Position(x, y);
        }
        double maxY = existing.stream().mapToDouble(n -> n.position().y()).max().orElse(-Y_GAP);
        return new FlowNode.Position(0, maxY + Y_GAP);
    }

    private static boolean occupied(List<FlowNode> nodes, double x, double y) {
        return nodes.stream().anyMatch(n -> Math.abs(n.position().x() - x) < X_GAP / 2
                && Math.abs(n.position().y() - y) < Y_GAP / 2);
    }
}
