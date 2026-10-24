package org.openideastudio.conversation;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The validated output schema of the {@code discovery-turn} AI operation. Every
 * field is untrusted model output: keys are never taken from here except to
 * reference existing open questions, which the orchestrator checks.
 */
public record DiscoveryTurn(
        @NotBlank @Size(max = 4000) String reply,
        @Valid IdeaSummary ideaSummary,
        @Valid NextQuestion nextQuestion,
        @NotNull @Size(max = 8) List<@Valid @NotNull Proposal> proposals,
        @Size(max = 10) List<@Valid @NotNull AnsweredQuestion> answeredQuestions) {

    public record IdeaSummary(
            @NotBlank @Size(max = 2000) String summary,
            @Size(max = 10) List<@NotBlank @Size(max = 200) String> targetUsers,
            @Size(max = 2000) String problem) {
    }

    public record NextQuestion(
            @NotBlank @Size(max = 500) String text,
            @Size(max = 1000) String why,
            @Size(max = 6) List<@NotBlank @Size(max = 200) String> options) {
    }

    public record Proposal(
            @NotBlank @Pattern(regexp = "requirement|decision|assumption|risk|question") String type,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @Pattern(regexp = "functional|non-functional|business-rule|security-privacy|operational|out-of-scope")
            String category,
            @Pattern(regexp = "must|should|could") String priority,
            @Size(max = 10) List<@NotBlank @Size(max = 500) String> acceptanceCriteria,
            @Size(max = 1000) String rationale,
            @Pattern(regexp = "low|medium|high") String impact) {
    }

    public record AnsweredQuestion(
            @NotBlank @Size(max = 100) String key,
            @NotBlank @Size(max = 2000) String answer) {
    }
}
