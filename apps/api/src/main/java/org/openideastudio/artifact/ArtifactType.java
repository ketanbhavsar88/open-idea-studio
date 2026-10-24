package org.openideastudio.artifact;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Artifact types, their key prefixes, allowed statuses, and initial statuses.
 * AI-created artifacts always start in a non-confirmed status; only a user can
 * confirm them (design doc §5.4).
 */
public enum ArtifactType {
    REQUIREMENT("requirement", "REQ", "proposed",
            List.of("proposed", "confirmed", "needs-clarification", "deferred", "rejected")),
    DECISION("decision", "DEC", "proposed", List.of("proposed", "confirmed", "reversed", "rejected")),
    ASSUMPTION("assumption", "ASM", "unconfirmed", List.of("unconfirmed", "confirmed", "rejected")),
    QUESTION("question", "Q", "open", List.of("open", "answered", "skipped")),
    RISK("risk", "RISK", "open", List.of("open", "mitigated", "accepted", "dismissed")),
    SUGGESTION("suggestion", "SUG", "proposed", List.of("proposed", "accepted", "rejected")),
    SUMMARY("summary", "SUMMARY", "ai-generated", List.of("ai-generated", "edited")),
    FLOW("flow", "FLOW", "draft", List.of("draft", "approved", "archived")),
    PROTOTYPE("prototype", "PROTOTYPE", "draft", List.of("draft", "approved"));

    private final String id;
    private final String prefix;
    private final String initialStatus;
    private final List<String> statuses;

    ArtifactType(String id, String prefix, String initialStatus, List<String> statuses) {
        this.id = id;
        this.prefix = prefix;
        this.initialStatus = initialStatus;
        this.statuses = statuses;
    }

    @JsonValue
    public String id() {
        return id;
    }

    public String prefix() {
        return prefix;
    }

    public String initialStatus() {
        return initialStatus;
    }

    public List<String> statuses() {
        return statuses;
    }

    public boolean allows(String status) {
        return statuses.contains(status);
    }

    public static ArtifactType from(String id) {
        return Arrays.stream(values()).filter(t -> t.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown artifact type: " + id));
    }
}
