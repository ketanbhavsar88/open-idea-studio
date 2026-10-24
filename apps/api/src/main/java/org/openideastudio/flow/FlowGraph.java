package org.openideastudio.flow;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The stored, documented graph of a flow (docs/data-model.md). Direct canvas
 * edits and AI proposals both produce this same structure.
 */
public record FlowGraph(
        @NotNull @Size(max = FlowGraph.MAX_NODES) List<@Valid @NotNull FlowNode> nodes,
        @NotNull @Size(max = FlowGraph.MAX_EDGES) List<@Valid @NotNull FlowEdge> edges,
        int schemaVersion) {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_NODES = 150;
    public static final int MAX_EDGES = 300;

    public static FlowGraph empty() {
        return new FlowGraph(List.of(), List.of(), SCHEMA_VERSION);
    }
}
