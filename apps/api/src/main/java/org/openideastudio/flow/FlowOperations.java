package org.openideastudio.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies operations to a graph without side effects; used for preview and for apply. */
public final class FlowOperations {

    private FlowOperations() {
    }

    /**
     * @param graph    the resulting graph (meaningful only when errors is empty)
     * @param resolved the operations with backend-assigned IDs and positions filled in
     */
    public record Result(FlowGraph graph, List<FlowOperation> resolved, List<String> errors) {
    }

    public static Result apply(FlowGraph graph, List<FlowOperation> operations) {
        Map<String, FlowNode> nodes = new LinkedHashMap<>();
        graph.nodes().forEach(n -> nodes.put(n.id(), n));
        Map<String, FlowEdge> edges = new LinkedHashMap<>();
        graph.edges().forEach(e -> edges.put(e.id(), e));
        Set<String> takenNodeIds = new HashSet<>(nodes.keySet());
        Set<String> takenEdgeIds = new HashSet<>(edges.keySet());
        Map<String, String> refs = new HashMap<>();
        List<FlowOperation> resolved = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < operations.size(); i++) {
            FlowOperation op = operations.get(i);
            String where = "Operation " + (i + 1) + " (" + op.op() + ")";
            switch (op.op()) {
                case "addNode" -> {
                    if (blank(op.label()) || op.nodeType() == null) {
                        errors.add(where + " needs label and nodeType.");
                        continue;
                    }
                    String id = op.nodeId() != null ? op.nodeId() : FlowIds.node(op.label(), takenNodeIds);
                    if (nodes.containsKey(id)) {
                        errors.add(where + ": node '" + id + "' already exists.");
                        continue;
                    }
                    takenNodeIds.add(id);
                    if (op.ref() != null) {
                        refs.put(op.ref(), id);
                    }
                    FlowNode.Position position = op.position() != null ? op.position()
                            : FlowLayout.near(anchorFor(op.ref(), operations, nodes, refs), List.copyOf(nodes.values()));
                    nodes.put(id, new FlowNode(id, op.nodeType(), op.label().strip(), op.description(), position,
                            op.requirementKeys()));
                    resolved.add(new FlowOperation("addNode", id, op.ref(), op.nodeType(), op.label().strip(),
                            op.description(), op.requirementKeys(), null, null, null, null, position));
                }
                case "updateNode" -> {
                    FlowNode n = op.nodeId() == null ? null : nodes.get(resolve(op.nodeId(), refs));
                    if (n == null) {
                        errors.add(where + ": unknown node '" + op.nodeId() + "'.");
                        continue;
                    }
                    nodes.put(n.id(), new FlowNode(n.id(),
                            op.nodeType() != null ? op.nodeType() : n.type(),
                            !blank(op.label()) ? op.label().strip() : n.label(),
                            op.description() != null ? op.description() : n.description(),
                            n.position(),
                            op.requirementKeys() != null ? op.requirementKeys() : n.requirementKeys()));
                    resolved.add(new FlowOperation("updateNode", n.id(), null, op.nodeType(), op.label(),
                            op.description(), op.requirementKeys(), null, null, null, null, null));
                }
                case "removeNode" -> {
                    String id = op.nodeId() == null ? null : resolve(op.nodeId(), refs);
                    if (id == null || nodes.remove(id) == null) {
                        errors.add(where + ": unknown node '" + op.nodeId() + "'.");
                        continue;
                    }
                    edges.values().removeIf(e -> e.source().equals(id) || e.target().equals(id));
                    resolved.add(new FlowOperation("removeNode", id, null, null, null, null, null,
                            null, null, null, null, null));
                }
                case "addEdge" -> {
                    String from = op.from() == null ? null : resolve(op.from(), refs);
                    String to = op.to() == null ? null : resolve(op.to(), refs);
                    if (from == null || !nodes.containsKey(from) || to == null || !nodes.containsKey(to)) {
                        errors.add(where + ": 'from' and 'to' must be existing node IDs or refs of added nodes.");
                        continue;
                    }
                    String id = op.edgeId() != null ? op.edgeId() : FlowIds.edge(from, to, takenEdgeIds);
                    if (edges.containsKey(id)) {
                        errors.add(where + ": edge '" + id + "' already exists.");
                        continue;
                    }
                    takenEdgeIds.add(id);
                    edges.put(id, new FlowEdge(id, from, to, op.edgeLabel()));
                    resolved.add(new FlowOperation("addEdge", null, null, null, null, null, null,
                            from, to, id, op.edgeLabel(), null));
                }
                case "removeEdge" -> {
                    if (op.edgeId() == null || edges.remove(op.edgeId()) == null) {
                        errors.add(where + ": unknown edge '" + op.edgeId() + "'.");
                        continue;
                    }
                    resolved.add(new FlowOperation("removeEdge", null, null, null, null, null, null,
                            null, null, op.edgeId(), null, null));
                }
                default -> errors.add(where + ": unknown operation.");
            }
        }
        FlowGraph result = new FlowGraph(List.copyOf(nodes.values()), List.copyOf(edges.values()),
                FlowGraph.SCHEMA_VERSION);
        return new Result(result, resolved, errors);
    }

    /** Places a new node below the node that the same proposal connects into it. */
    private static FlowNode anchorFor(String ref, List<FlowOperation> ops, Map<String, FlowNode> nodes,
                                      Map<String, String> refs) {
        if (ref == null) {
            return null;
        }
        return ops.stream()
                .filter(o -> o.op().equals("addEdge") && ref.equals(o.to()) && o.from() != null)
                .map(o -> nodes.get(resolve(o.from(), refs)))
                .filter(n -> n != null)
                .findFirst().orElse(null);
    }

    private static String resolve(String idOrRef, Map<String, String> refs) {
        return refs.getOrDefault(idOrRef, idOrRef);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
