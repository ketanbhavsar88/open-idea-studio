package org.openideastudio.revision;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Revision metadata, without the (potentially large) snapshot. */
public record Revision(
        UUID id,
        UUID projectId,
        UUID parentRevisionId,
        int number,
        String summary,
        String createdBy,
        List<String> affectedArtifacts,
        Instant createdAt) {

    public static final String BY_USER = "user";
    public static final String BY_SYSTEM = "system";
    public static final String BY_AI = "ai-proposal";
}
