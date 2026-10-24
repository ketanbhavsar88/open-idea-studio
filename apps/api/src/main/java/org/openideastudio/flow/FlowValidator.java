package org.openideastudio.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Semantic checks beyond the field constraints: unique IDs, edges that point at
 * real nodes, links to real requirements. Errors block saving; warnings are shown
 * to the user but allow saving work in progress.
 */
public final class FlowValidator {

    private FlowValidator() {
    }

    public record Result(List<String> errors, List<String> warnings) {

        public boolean valid() {
            return errors.isEmpty();
        }
    }

    public static Result validate(FlowGraph graph, Set<String> requirementKeys) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<String, FlowNode> nodes = new HashMap<>();
        for (FlowNode n : graph.nodes()) {
            if (nodes.put(n.id(), n) != null) {
                errors.add("Duplicate node id '" + n.id() + "'.");
            }
            for (String key : n.requirementKeysOrEmpty()) {
                if (!requirementKeys.contains(key)) {
                    errors.add("Node '" + n.id() + "' links to unknown requirement '" + key + "'.");
                }
            }
        }

        Set<String> edgeIds = new HashSet<>();
        Set<String> pairs = new HashSet<>();
        Map<String, Integer> outgoing = new HashMap<>();
        Map<String, Integer> incoming = new HashMap<>();
        for (FlowEdge e : graph.edges()) {
            if (!edgeIds.add(e.id())) {
                errors.add("Duplicate edge id '" + e.id() + "'.");
            }
            if (!nodes.containsKey(e.source())) {
                errors.add("Edge '" + e.id() + "' starts at unknown node '" + e.source() + "'.");
            }
            if (!nodes.containsKey(e.target())) {
                errors.add("Edge '" + e.id() + "' ends at unknown node '" + e.target() + "'.");
            }
            if (e.source().equals(e.target())) {
                errors.add("Edge '" + e.id() + "' connects a node to itself.");
            }
            if (!pairs.add(e.source() + "->" + e.target() + ":" + e.label())) {
                warnings.add("More than one identical connection from '" + e.source() + "' to '" + e.target() + "'.");
            }
            outgoing.merge(e.source(), 1, Integer::sum);
            incoming.merge(e.target(), 1, Integer::sum);
        }

        if (!graph.nodes().isEmpty()) {
            long starts = graph.nodes().stream().filter(n -> n.type().equals("start")).count();
            if (starts == 0) {
                warnings.add("The flow has no start node.");
            }
            if (graph.nodes().stream().noneMatch(n -> n.type().equals("end"))) {
                warnings.add("The flow has no end node.");
            }
        }
        for (FlowNode n : graph.nodes()) {
            int out = outgoing.getOrDefault(n.id(), 0);
            int in = incoming.getOrDefault(n.id(), 0);
            switch (n.type()) {
                case "start" -> {
                    if (in > 0) {
                        warnings.add("Start node '" + n.label() + "' has incoming connections.");
                    }
                }
                case "end" -> {
                    if (out > 0) {
                        warnings.add("End node '" + n.label() + "' has outgoing connections.");
                    }
                }
                case "decision" -> {
                    if (out < 2) {
                        warnings.add("Decision '" + n.label() + "' should have at least two outcomes.");
                    }
                }
                default -> {
                }
            }
            if (in == 0 && out == 0 && graph.nodes().size() > 1) {
                warnings.add("'" + n.label() + "' is not connected.");
            }
        }
        return new Result(errors, warnings);
    }
}
