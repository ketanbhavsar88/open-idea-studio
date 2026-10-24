package org.openideastudio.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FlowOperationsTest {

    static final FlowGraph BOOKING = new FlowGraph(List.of(
            node("node-start", "start", "Start", 0, 0),
            node("node-request", "action", "Request booking", 0, 140),
            node("node-pay", "action", "Pay", 0, 280),
            node("node-end", "end", "Done", 0, 420)),
            List.of(edge("e1", "node-start", "node-request"), edge("e2", "node-request", "node-pay"),
                    edge("e3", "node-pay", "node-end")),
            1);

    @Test
    void insertsAStepWithoutTouchingUnrelatedNodes() {
        List<FlowOperation> ops = List.of(
                op("removeEdge", null, null, null, null, null, "e2"),
                op("addNode", null, "new1", "decision", "Owner approves?", null, null),
                op("addEdge", null, null, null, "node-request", "new1", null),
                op("addEdge", null, null, null, "new1", "node-pay", null));

        FlowOperations.Result result = FlowOperations.apply(BOOKING, ops);

        assertThat(result.errors()).isEmpty();
        assertThat(result.graph().nodes()).extracting(FlowNode::id)
                .containsExactly("node-start", "node-request", "node-pay", "node-end", "node-owner-approves");
        // Existing nodes keep their positions; the new one is placed below its source.
        assertThat(result.graph().nodes().get(2).position()).isEqualTo(new FlowNode.Position(0, 280));
        assertThat(result.graph().nodes().get(4).position().y()).isEqualTo(280);
        assertThat(result.graph().edges()).extracting(FlowEdge::id).doesNotContain("e2")
                .contains("e-request-owner-approves", "e-owner-approves-pay");
        // Resolved operations carry concrete IDs so re-applying is deterministic.
        assertThat(result.resolved().get(1).nodeId()).isEqualTo("node-owner-approves");
        assertThat(FlowOperations.apply(BOOKING, result.resolved()).graph()).isEqualTo(result.graph());
    }

    @Test
    void removingANodeRemovesItsConnections() {
        FlowOperations.Result result = FlowOperations.apply(BOOKING,
                List.of(op("removeNode", "node-pay", null, null, null, null, null)));
        assertThat(result.graph().edges()).extracting(FlowEdge::id).containsExactly("e1");
    }

    @Test
    void reportsUnknownReferences() {
        FlowOperations.Result result = FlowOperations.apply(BOOKING, List.of(
                op("updateNode", "node-missing", null, null, null, null, null),
                op("addEdge", null, null, null, "node-start", "ghost", null),
                op("removeEdge", null, null, null, null, null, "e99")));
        assertThat(result.errors()).hasSize(3);
    }

    @Test
    void validatorFlagsBrokenGraphsAndWarnsAboutIncompleteOnes() {
        FlowGraph broken = new FlowGraph(List.of(node("a", "start", "A", 0, 0), node("a", "decision", "B", 0, 0)),
                List.of(edge("e1", "a", "zzz")), 1);
        FlowValidator.Result result = FlowValidator.validate(broken, Set.of());
        assertThat(result.errors()).anyMatch(e -> e.contains("Duplicate node id"))
                .anyMatch(e -> e.contains("unknown node 'zzz'"));

        FlowGraph linked = new FlowGraph(List.of(new FlowNode("a", "action", "A", null,
                new FlowNode.Position(0, 0), List.of("REQ-NOPE"))), List.of(), 1);
        assertThat(FlowValidator.validate(linked, Set.of("REQ-OK")).errors())
                .containsExactly("Node 'a' links to unknown requirement 'REQ-NOPE'.");

        assertThat(FlowValidator.validate(BOOKING, Set.of()).valid()).isTrue();
        assertThat(FlowValidator.validate(BOOKING, Set.of()).warnings()).isEmpty();
    }

    @Test
    void layoutPlacesNodesInRowsByDepth() {
        List<FlowNode> laidOut = FlowLayout.layout(BOOKING.nodes(), BOOKING.edges());
        assertThat(laidOut).extracting(n -> n.position().y()).containsExactly(0.0, 140.0, 280.0, 420.0);
    }

    static FlowNode node(String id, String type, String label, double x, double y) {
        return new FlowNode(id, type, label, null, new FlowNode.Position(x, y), List.of());
    }

    static FlowEdge edge(String id, String from, String to) {
        return new FlowEdge(id, from, to, null);
    }

    static FlowOperation op(String op, String nodeId, String ref, String type, String from, String to, String edgeId) {
        String label = op.equals("addNode") ? "Owner approves?" : null;
        return new FlowOperation(op, nodeId, ref, type, label, null, null,
                op.equals("addEdge") ? from : null,
                op.equals("addEdge") ? to : null, edgeId, null, null);
    }
}
