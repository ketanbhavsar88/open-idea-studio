package org.openideastudio.flow;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Validated output of the generate-flow operation; refs are replaced by backend IDs. */
record GeneratedFlow(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotNull @Size(min = 2, max = 40) List<@Valid @NotNull Node> nodes,
        @NotNull @Size(max = 80) List<@Valid @NotNull Edge> edges) {

    record Node(
            @NotBlank @Size(max = 40) String ref,
            @NotBlank @Pattern(regexp = "start|end|action|decision|subprocess") String type,
            @NotBlank @Size(max = 120) String label,
            @Size(max = 1000) String description,
            @Size(max = 20) List<@NotBlank String> requirementKeys) {
    }

    record Edge(@NotBlank String from, @NotBlank String to, @Size(max = 80) String label) {
    }
}
