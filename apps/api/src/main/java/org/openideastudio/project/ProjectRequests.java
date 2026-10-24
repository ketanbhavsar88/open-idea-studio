package org.openideastudio.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ProjectRequests {

    private ProjectRequests() {
    }

    public record Create(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 20_000) String description) {
    }

    /** Partial update. {@code version} must match the current project version. */
    public record Update(
            @Size(min = 1, max = 200) String name,
            @Size(max = 20_000) String description,
            @NotNull Long version) {
    }
}
