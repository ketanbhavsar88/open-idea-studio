package org.openideastudio.artifact;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.openideastudio.flow.FlowGraph;

/**
 * Typed payload stored in {@code artifact.data}. One shape for all Phase 1
 * types keeps validation in one place; fields that do not apply stay null.
 *
 * @param origin   "user" or "ai"
 * @param answer   for questions: the user's answer
 * @param why      for questions: why it matters
 * @param graph    for flows: the node/edge graph
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ArtifactData(
        @Size(max = 5000) String description,
        @Pattern(regexp = "functional|non-functional|business-rule|security-privacy|operational|out-of-scope")
        String category,
        @Pattern(regexp = "must|should|could") String priority,
        @Size(max = 20) List<@Size(max = 1000) String> acceptanceCriteria,
        @Size(max = 2000) String rationale,
        @Pattern(regexp = "low|medium|high") String impact,
        @Size(max = 5000) String answer,
        @Size(max = 1000) String why,
        @Size(max = 8) List<@Size(max = 300) String> options,
        @Size(max = 20) List<@Size(max = 300) String> targetUsers,
        @Size(max = 2000) String problem,
        @Pattern(regexp = "user|ai") String origin,
        @Valid FlowGraph graph) {

    public static ArtifactData empty() {
        return new ArtifactData(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public ArtifactData withOrigin(String value) {
        return new ArtifactData(description, category, priority, acceptanceCriteria, rationale, impact, answer, why,
                options, targetUsers, problem, value, graph);
    }
}
