package org.openideastudio.prototype;

import java.util.List;
import java.util.stream.Collectors;

import org.openideastudio.artifact.Artifact;
import org.openideastudio.flow.FlowGraph;

/** Instructions and context for the generate-prototype and propose-screen operations. */
final class PrototypePrompts {

    private PrototypePrompts() {
    }

    static final String COMPONENTS = """
            Components (field "type" selects the kind; omit optional fields or use null):
            - {"type":"header","text":string,"subtitle":string|null}
            - {"type":"text","text":string,"variant":"body"|"caption"|"heading"}
            - {"type":"alert","text":string,"tone":"neutral"|"info"|"success"|"warning"|"danger"}
            - {"type":"badge","text":string,"tone":...}
            - {"type":"button","label":string,"variant":"primary"|"secondary"|"danger","action":ACTION}
            - {"type":"card","title":string|null,"body":string|null,"children":[component]}
            - {"type":"list","items":[{"title":string,"subtitle":string|null,"meta":string|null,"badge":string|null,"action":ACTION|null}]}
            - {"type":"table","columns":[string],"rows":[[string]]}
            - {"type":"form","fields":[{"name":camelCase,"label":string,"inputType":"text"|"number"|"date"|"email"|"select"|"textarea"|"checkbox","options":[string]|null,"required":bool,"placeholder":string|null}],"submitLabel":string,"action":ACTION}
            - {"type":"tabs","tabs":[{"label":string,"children":[component]}]}
            - {"type":"dialog","triggerLabel":string,"title":string,"body":string|null,"confirmLabel":string,"action":ACTION}
            - {"type":"image","alt":string}                     // grey placeholder, never a URL
            - {"type":"conditional","key":stateKey,"equals":string,"children":[component]}   // shown only when state[key] == equals

            ACTION is one of:
            - {"type":"navigate","target":screenId}
            - {"type":"message","message":string}               // shown as a simulated notice
            - {"type":"setState","key":stateKey,"value":string,"target":screenId|null}   // optional navigate after

            Text may include {state.someKey} to show a state value (e.g. "Status: {state.bookingStatus}").
            Nesting depth at most 4. No HTML, markdown, URLs, emails of real people, or code anywhere.
            """;

    static final String GENERATE = """
            You design a LOW-FIDELITY, CLICKABLE prototype for Open Idea Studio, as a JSON specification that
            is rendered by a fixed set of trusted components. You never write code.

            Rules:
            - 4-8 screens covering the main journey in the flows and confirmed requirements.
            - Every screen must be reachable via navigate actions; include a way back where natural.
            - Use realistic but clearly fictional sample data (no real people, brands, or contact details).
            - Anything that would need a backend (payment, sign-in, sending messages) must be a simulated
              action: a "message" saying what would happen, and/or "setState" to show the new status.
            - Link each screen to the requirement keys it implements, using ONLY the keys listed.
            - Screen ids: short lowercase like "home", "listing-detail". If an existing screen covers the same
              purpose, reuse its id and title.

            """ + COMPONENTS + """

            Output JSON schema:
            {
              "title": string,
              "startScreen": screenId,
              "initialState": { stateKey: string },
              "screens": [{ "id": string, "title": string, "description": string|null,
                            "requirementKeys": [string], "components": [component] }],
              "schemaVersion": 1
            }
            """;

    static final String PROPOSE_SCREEN = """
            You redesign ONE screen of a clickable prototype in Open Idea Studio, following the user's
            instruction. Keep the screen's purpose unless told otherwise. Navigate only to the screen ids
            listed. Use only listed requirement keys. Keep simulated behaviour clearly simulated.

            """ + COMPONENTS + """

            Output JSON schema (the screen only):
            { "id": string, "title": string, "description": string|null, "requirementKeys": [string],
              "components": [component] }
            """;

    static String context(String projectName, Artifact summary, List<Artifact> requirements,
                          List<Artifact> decisions, List<Artifact> flows) {
        StringBuilder s = new StringBuilder("Project: ").append(projectName).append('\n');
        if (summary != null && summary.data().description() != null) {
            s.append("Idea summary: ").append(summary.data().description()).append('\n');
        }
        s.append(requirements.stream()
                .map(r -> "- [" + r.key() + "] " + r.title()
                        + (r.data().description() == null ? "" : ": " + r.data().description()))
                .collect(Collectors.joining("\n", "Confirmed requirements:\n", "\n")));
        if (!decisions.isEmpty()) {
            s.append(decisions.stream().map(d -> "- " + d.title())
                    .collect(Collectors.joining("\n", "Confirmed decisions:\n", "\n")));
        }
        for (Artifact f : flows) {
            FlowGraph g = f.data().graph();
            if (g == null) {
                continue;
            }
            s.append("Flow \"").append(f.title()).append("\": ");
            s.append(g.edges().stream().map(e -> label(g, e.source()) + " -> "
                            + (e.label() == null ? "" : "[" + e.label() + "] ") + label(g, e.target()))
                    .collect(Collectors.joining("; "))).append('\n');
        }
        return s.toString();
    }

    static String existingScreens(PrototypeSpec spec) {
        if (spec == null) {
            return "Existing screens: (none)\n";
        }
        return spec.screens().stream().map(sc -> "- " + sc.id() + ": " + sc.title())
                .collect(Collectors.joining("\n", "Existing screens:\n", "\n"));
    }

    private static String label(FlowGraph g, String nodeId) {
        return g.nodes().stream().filter(n -> n.id().equals(nodeId)).map(n -> n.label()).findFirst().orElse(nodeId);
    }
}
