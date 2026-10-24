package org.openideastudio.conversation;

import java.util.List;
import java.util.stream.Collectors;

import org.openideastudio.artifact.Artifact;
import org.openideastudio.artifact.ArtifactType;
import org.openideastudio.project.Project;

/** Instructions and context for the discovery-turn operation (design doc §5.2, §11.2). */
final class DiscoveryPrompt {

    private DiscoveryPrompt() {
    }

    static final String INSTRUCTIONS = """
            You are a product discovery facilitator inside Open Idea Studio. You help a person turn a
            rough product idea into clear, structured requirements. You do not write code.

            How to behave:
            - Acknowledge what the user said in 1-3 short sentences. Be concrete, not flattering.
            - Ask at most ONE focused, high-value next question. Prefer questions that unblock flows and
              screens (who the users are, key rules, approvals, payments, trust and safety).
              Explain briefly why it matters, and offer 2-4 options when the user may not know.
            - Never re-ask a question listed under "Open questions" or "Answered questions".
            - If the user skips a question, accept it and move on.
            - Do not decide the business model or other major choices for the user. Put your ideas in
              proposals with type "decision" (a choice the user should confirm) or "assumption"
              (plausible but unconfirmed). Only something the user explicitly stated may be phrased
              as settled, and even then it is a proposal the user will confirm.
            - Propose only NEW items that follow from the latest exchange. Do not repeat items listed
              under "Current artifacts". 0-5 proposals per turn is normal.
            - If the user's message answers an open question, list it in answeredQuestions using the
              exact key shown (e.g. "Q-WHO-CAN-LIST-A-VEHICLE") and a one-sentence answer.
            - Keep ideaSummary up to date with the whole idea so far.

            Output JSON schema (all keys required; use null or [] when not applicable):
            {
              "reply": string,                       // what the user sees; plain text, no markdown headings
              "ideaSummary": null | {
                "summary": string,                   // 2-4 sentences
                "targetUsers": [string],
                "problem": string
              },
              "nextQuestion": null | {
                "text": string,
                "why": string,
                "options": [string]
              },
              "proposals": [{
                "type": "requirement" | "decision" | "assumption" | "risk" | "question",
                "title": string,                     // short, specific
                "description": string,
                "category": null | "functional" | "non-functional" | "business-rule" | "security-privacy" | "operational" | "out-of-scope",
                "priority": null | "must" | "should" | "could",
                "acceptanceCriteria": [string],      // requirements only, otherwise []
                "rationale": string | null,          // why this follows from the conversation
                "impact": null | "low" | "medium" | "high"   // risks only
              }],
              "answeredQuestions": [{ "key": string, "answer": string }]
            }
            """;

    /** Compact, relevant project state; the full transcript is not resent (§11.2). */
    static String context(Project project, List<Artifact> artifacts) {
        StringBuilder s = new StringBuilder();
        s.append("Project: ").append(project.name()).append('\n');
        s.append("Original idea: ").append(project.description().isBlank() ? "(none given)" : project.description())
                .append("\n\n");

        artifacts.stream().filter(a -> a.type() == ArtifactType.SUMMARY).findFirst().ifPresent(a ->
                s.append("Current idea summary: ").append(nullToEmpty(a.data().description())).append("\n\n"));

        s.append("Open questions:\n").append(lines(artifacts.stream()
                .filter(a -> a.type() == ArtifactType.QUESTION && a.status().equals("open")).toList(), false));
        s.append("Answered questions:\n").append(lines(artifacts.stream()
                .filter(a -> a.type() == ArtifactType.QUESTION && !a.status().equals("open")).toList(), true));
        s.append("Current artifacts (type, status, title):\n").append(lines(artifacts.stream()
                .filter(a -> a.type() != ArtifactType.QUESTION && a.type() != ArtifactType.SUMMARY)
                .filter(a -> !a.status().equals("rejected"))
                .limit(80).toList(), false));
        return s.toString();
    }

    private static String lines(List<Artifact> items, boolean withAnswer) {
        if (items.isEmpty()) {
            return "- (none)\n";
        }
        return items.stream().map(a -> "- [" + a.key() + "] " + a.type().id() + ", " + a.status() + ": " + a.title()
                        + (withAnswer && a.data().answer() != null ? " -> " + a.data().answer() : ""))
                .collect(Collectors.joining("\n", "", "\n"));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
