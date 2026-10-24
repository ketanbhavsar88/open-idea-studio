package org.openideastudio.flow;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One targeted graph change. Proposals contain only these, never a whole
 * replacement graph, so unrelated nodes keep their IDs, positions and edits.
 *
 * <ul>
 *   <li>addNode: {@code ref} (temporary, for edges in the same proposal), nodeType, label, description, requirementKeys</li>
 *   <li>updateNode: {@code nodeId} plus any of label, description, nodeType, requirementKeys</li>
 *   <li>removeNode: {@code nodeId}; its connections are removed too</li>
 *   <li>addEdge: {@code from}, {@code to} (node IDs or refs), optional edgeLabel</li>
 *   <li>removeEdge: {@code edgeId}</li>
 * </ul>
 * After resolution the backend fills {@code nodeId}/{@code edgeId}/{@code position}
 * for added items, so applying a stored proposal is deterministic.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlowOperation(
        @NotBlank @Pattern(regexp = "addNode|updateNode|removeNode|addEdge|removeEdge") String op,
        @Size(max = 80) String nodeId,
        @Size(max = 40) String ref,
        @Pattern(regexp = "start|end|action|decision|subprocess") String nodeType,
        @Size(max = 120) String label,
        @Size(max = 1000) String description,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> requirementKeys,
        @Size(max = 80) String from,
        @Size(max = 80) String to,
        @Size(max = 80) String edgeId,
        @Size(max = 80) String edgeLabel,
        @Valid FlowNode.Position position) {
}
