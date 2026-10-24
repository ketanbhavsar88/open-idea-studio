package org.openideastudio.artifact;

import java.time.Instant;

/** @param key stable, human-readable ID (REQ-OWNER-APPROVAL) that survives edits */
public record Artifact(
        String key,
        ArtifactType type,
        String title,
        String status,
        ArtifactData data,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
