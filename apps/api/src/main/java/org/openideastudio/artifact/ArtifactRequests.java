package org.openideastudio.artifact;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ArtifactRequests {

    private ArtifactRequests() {
    }

    public record Create(
            @NotBlank String type,
            @NotBlank @Size(max = 300) String title,
            String status,
            @Valid ArtifactData data) {
    }

    /** Partial update; {@code data} replaces the whole payload when present. */
    public record Update(
            @Size(min = 1, max = 300) String title,
            String status,
            @Valid ArtifactData data,
            @NotNull Long version) {
    }
}
