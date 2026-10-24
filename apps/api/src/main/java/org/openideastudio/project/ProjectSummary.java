package org.openideastudio.project;

/**
 * Transparent progress counts for the dashboard, used instead of a single
 * "completeness score" (design doc §6.1).
 */
public record ProjectSummary(Project project, Counts counts) {

    public record Counts(int confirmedRequirements, int openQuestions, int flows, int screens) {
    }
}
