package org.openideastudio.flow;

import java.util.List;
import java.util.stream.Collectors;

import org.openideastudio.artifact.Artifact;

/** Instructions and context for the generate-flow and propose-flow-change operations. */
final class FlowPrompts {

    private FlowPrompts() {
    }

    static final String GENERATE = """
            You design user/product flow diagrams for Open Idea Studio from confirmed requirements.

            Rules:
            - Model ONE coherent journey (e.g. booking, onboarding) as a directed graph.
            - Exactly one "start" node and at least one "end" node.
            - Use "decision" nodes for branches, with one outgoing edge per outcome and a short edge label
              ("Approved", "Rejected"). Use "action" for steps, "subprocess" for a step detailed elsewhere.
            - 5-15 nodes. Short labels (max ~6 words); put detail in description.
            - Link each node to the requirement keys it implements, using ONLY the keys listed. Do not invent keys.
            - Include only behaviour supported by the requirements and decisions; do not invent features.
            - Give every node a unique "ref" like "n1", "n2"; edges use refs in "from"/"to".

            Output JSON schema:
            {
              "title": string,                 // e.g. "Booking flow"
              "description": string,
              "nodes": [{ "ref": string, "type": "start"|"end"|"action"|"decision"|"subprocess",
                          "label": string, "description": string|null, "requirementKeys": [string] }],
              "edges": [{ "from": string, "to": string, "label": string|null }]
            }
            """;

    static final String PROPOSE_CHANGE = """
            You edit an existing flow diagram in Open Idea Studio by proposing a MINIMAL list of operations.
            Change only what the user's instruction requires; never recreate or rename unrelated nodes.

            Operations (use only these fields; omit or null the rest):
            - {"op":"addNode","ref":"new1","nodeType":"start|end|action|decision|subprocess","label":string,"description":string|null,"requirementKeys":[string]}
            - {"op":"updateNode","nodeId":string,"label":string|null,"description":string|null,"nodeType":string|null,"requirementKeys":[string]|null}
            - {"op":"removeNode","nodeId":string}            // also removes its connections
            - {"op":"addEdge","from":string,"to":string,"edgeLabel":string|null}   // node ids or refs of added nodes
            - {"op":"removeEdge","edgeId":string}

            To insert a step between A and B: remove the edge A->B, add the node, add A->new and new->B.
            Use node and edge ids exactly as listed. Requirement keys must come from the list given.
            If the instruction is unclear or impossible, return no operations and explain in "warnings".

            Output JSON schema:
            {
              "summary": string,               // one sentence describing the change
              "operations": [operation],
              "warnings": [string]
            }
            """;

    static String requirements(List<Artifact> requirements) {
        if (requirements.isEmpty()) {
            return "Requirements: (none)\n";
        }
        return requirements.stream().map(r -> {
            String line = "- [" + r.key() + "] (" + r.status() + ") " + r.title();
            if (r.data().description() != null) {
                line += ": " + r.data().description();
            }
            if (r.data().acceptanceCriteria() != null && !r.data().acceptanceCriteria().isEmpty()) {
                line += " | Acceptance: " + String.join("; ", r.data().acceptanceCriteria());
            }
            return line;
        }).collect(Collectors.joining("\n", "Requirements:\n", "\n"));
    }

    static String decisions(List<Artifact> decisions) {
        if (decisions.isEmpty()) {
            return "";
        }
        return decisions.stream().map(d -> "- " + d.title()
                        + (d.data().description() != null ? ": " + d.data().description() : ""))
                .collect(Collectors.joining("\n", "Confirmed decisions:\n", "\n"));
    }

    static String graph(FlowGraph graph) {
        String nodes = graph.nodes().stream()
                .map(n -> "- " + n.id() + " [" + n.type() + "] \"" + n.label() + "\""
                        + (n.requirementKeysOrEmpty().isEmpty() ? "" : " implements " + n.requirementKeysOrEmpty()))
                .collect(Collectors.joining("\n"));
        String edges = graph.edges().stream()
                .map(e -> "- " + e.id() + ": " + e.source() + " -> " + e.target()
                        + (e.label() == null ? "" : " (" + e.label() + ")"))
                .collect(Collectors.joining("\n"));
        return "Nodes:\n" + (nodes.isEmpty() ? "- (none)" : nodes) + "\nEdges:\n" + (edges.isEmpty() ? "- (none)" : edges) + "\n";
    }
}
