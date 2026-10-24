package org.openideastudio.changeset;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.openideastudio.flow.FlowOperation;

/**
 * A reviewable proposal. It applies only while its target artifact is still at
 * {@code targetArtifactVersion}; otherwise it is stale and must be re-proposed.
 */
public record ChangeSet(
        UUID id,
        String kind,
        UUID baseRevisionId,
        String targetArtifactKey,
        long targetArtifactVersion,
        String instruction,
        String summary,
        List<FlowOperation> operations,
        List<String> warnings,
        String state,
        UUID appliedRevisionId,
        Instant createdAt,
        Instant appliedAt) {

    public static final String FLOW_EDIT = "flow-edit";
}
