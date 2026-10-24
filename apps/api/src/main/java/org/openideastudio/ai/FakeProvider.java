package org.openideastudio.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;

/**
 * Deterministic provider for automated tests and for trying the UI without a
 * model ({@code AI_PROVIDER=fake}). Tests can queue exact responses or failures;
 * otherwise a fixed, clearly labelled demo reply is returned.
 */
public class FakeProvider implements ModelProvider {

    static final String DEMO_RESPONSE = """
            {
              "reply": "[Fake AI provider: canned demo response, not a real model] Thanks, I've noted that.",
              "ideaSummary": {
                "summary": "Demo summary from the fake provider.",
                "targetUsers": ["Demo users"],
                "problem": "Demo problem statement."
              },
              "nextQuestion": {
                "text": "Who are the main people using this product?",
                "why": "Knowing the users shapes every flow and screen.",
                "options": ["Consumers", "Businesses", "Both"]
              },
              "proposals": []
            }
            """;

    static final String DEMO_FLOW = """
            {
              "title": "Demo flow (fake AI provider)",
              "description": "Canned demo flow from the fake provider, not generated from your requirements.",
              "nodes": [
                {"ref": "n1", "type": "start", "label": "Start", "description": null, "requirementKeys": []},
                {"ref": "n2", "type": "action", "label": "Do the main task", "description": null, "requirementKeys": []},
                {"ref": "n3", "type": "decision", "label": "Succeeded?", "description": null, "requirementKeys": []},
                {"ref": "n4", "type": "end", "label": "Done", "description": null, "requirementKeys": []},
                {"ref": "n5", "type": "end", "label": "Show error", "description": null, "requirementKeys": []}
              ],
              "edges": [
                {"from": "n1", "to": "n2", "label": null},
                {"from": "n2", "to": "n3", "label": null},
                {"from": "n3", "to": "n4", "label": "Yes"},
                {"from": "n3", "to": "n5", "label": "No"}
              ]
            }
            """;

    static final String DEMO_FLOW_CHANGE = """
            {"summary": "No change (fake AI provider).", "operations": [],
             "warnings": ["The fake AI provider cannot edit flows. Set AI_PROVIDER=gemini or ollama."]}
            """;

    static final String DEMO_PROTOTYPE = """
            {
              "title": "Demo prototype (fake AI provider)",
              "startScreen": "home",
              "initialState": {"status": "none"},
              "screens": [
                {"id": "home", "title": "Home", "description": "Canned demo screen, not generated from your project.",
                 "requirementKeys": [], "components": [
                   {"type": "alert", "text": "Demo content from the fake AI provider.", "tone": "info"},
                   {"type": "header", "text": "Welcome", "subtitle": "Pick an item to continue"},
                   {"type": "list", "items": [
                     {"title": "Sample item A", "subtitle": "Fictional sample data", "meta": "$10", "badge": null,
                      "action": {"type": "navigate", "target": "detail"}},
                     {"title": "Sample item B", "subtitle": "Fictional sample data", "meta": "$12", "badge": null,
                      "action": {"type": "navigate", "target": "detail"}}
                   ]}
                 ]},
                {"id": "detail", "title": "Detail", "description": null, "requirementKeys": [], "components": [
                   {"type": "header", "text": "Sample item A", "subtitle": null},
                   {"type": "image", "alt": "Item photo"},
                   {"type": "badge", "text": "Status: {state.status}", "tone": "neutral"},
                   {"type": "button", "label": "Request", "variant": "primary",
                    "action": {"type": "setState", "key": "status", "value": "requested", "target": null}},
                   {"type": "conditional", "key": "status", "equals": "requested", "children": [
                     {"type": "alert", "text": "Request sent (simulated).", "tone": "success"}
                   ]},
                   {"type": "button", "label": "Back", "variant": "secondary", "action": {"type": "navigate", "target": "home"}}
                 ]}
              ],
              "schemaVersion": 1
            }
            """;

    static final String DEMO_SCREEN = """
            {"id": "home", "title": "Home", "description": "Canned demo screen from the fake AI provider.",
             "requirementKeys": [], "components": [
               {"type": "alert", "text": "The fake AI provider cannot redesign screens.", "tone": "warning"},
               {"type": "button", "label": "Open detail", "variant": "primary", "action": {"type": "navigate", "target": "detail"}}
             ]}
            """;

    private final Deque<Function<ModelRequest, ModelResponse>> scripted = new ArrayDeque<>();

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public String defaultModel() {
        return "fake";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(true, false, 32_000);
    }

    @Override
    public synchronized ModelResponse generate(ModelRequest request) {
        Function<ModelRequest, ModelResponse> next = scripted.pollFirst();
        if (next != null) {
            return next.apply(request);
        }
        String demo = switch (request.operation() == null ? "" : request.operation()) {
            case "generate-flow" -> DEMO_FLOW;
            case "propose-flow-change" -> DEMO_FLOW_CHANGE;
            case "generate-prototype" -> DEMO_PROTOTYPE;
            case "propose-screen" -> DEMO_SCREEN;
            default -> DEMO_RESPONSE;
        };
        return new ModelResponse(demo, "fake");
    }

    public synchronized void enqueue(String responseText) {
        scripted.addLast(r -> new ModelResponse(responseText, "fake"));
    }

    public synchronized void enqueueFailure(ModelUnavailableException failure) {
        scripted.addLast(r -> {
            throw failure;
        });
    }

    public synchronized void reset() {
        scripted.clear();
    }
}
