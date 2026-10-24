package org.openideastudio.flow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A directed connection. {@code label} names the branch for decisions ("Approved"). */
public record FlowEdge(
        @NotBlank @Pattern(regexp = FlowNode.ID_PATTERN) String id,
        @NotBlank String source,
        @NotBlank String target,
        @Size(max = 80) String label) {
}
