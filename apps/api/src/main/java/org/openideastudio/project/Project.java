package org.openideastudio.project;

import java.time.Instant;
import java.util.UUID;

public record Project(
        UUID id,
        String name,
        String description,
        UUID currentRevisionId,
        int schemaVersion,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
