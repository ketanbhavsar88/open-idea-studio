package org.openideastudio.prototype;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openideastudio.TestcontainersConfiguration;
import org.openideastudio.ai.FakeProvider;
import org.openideastudio.ai.ModelProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(properties = {"ai.provider=fake", "ai.min-interval-ms=0"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PrototypeIntegrationTest {

    static final String GENERATED = """
            {"title": "Vehicle sharing", "startScreen": "s1", "initialState": {"bookingStatus": "none"},
             "screens": [
               {"id": "s1", "title": "Browse vehicles", "description": null,
                "requirementKeys": ["REQ-RENTERS-REQUEST-BOOKINGS"], "components": [
                  {"type": "list", "items": [{"title": "Compact hatchback", "subtitle": "2 km away", "meta": "$8/h",
                     "badge": null, "action": {"type": "navigate", "target": "s2"}}]}]},
               {"id": "s2", "title": "Request booking", "description": null,
                "requirementKeys": ["REQ-RENTERS-REQUEST-BOOKINGS"], "components": [
                  {"type": "form", "fields": [{"name": "start", "label": "Start", "inputType": "date",
                     "options": null, "required": true, "placeholder": null}],
                   "submitLabel": "Send request",
                   "action": {"type": "setState", "key": "bookingStatus", "value": "pending", "target": "s1"}}]}
             ], "schemaVersion": 1}
            """;

    static final String INVENTED_KEY = GENERATED.replace("\"REQ-RENTERS-REQUEST-BOOKINGS\"]", "\"REQ-INVENTED\"]");

    static final String SCREEN = """
            {"id": "whatever", "title": "Request booking", "description": "With a pending notice",
             "requirementKeys": [], "components": [
               {"type": "conditional", "key": "bookingStatus", "equals": "pending", "children": [
                 {"type": "alert", "text": "Waiting for the owner (simulated).", "tone": "info"}]},
               {"type": "button", "label": "Back", "variant": "secondary", "action": {"type": "navigate", "target": "browse-vehicles"}}]}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    ModelProvider provider;

    FakeProvider fake;

    @BeforeEach
    void setUp() {
        fake = (FakeProvider) provider;
        fake.reset();
    }

    @Test
    void generateDraftSaveAndRegenerateOneScreen() throws Exception {
        String projectId = projectWithConfirmedRequirement();
        mvc.perform(get("/api/v1/projects/{id}/prototype", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.prototype").value(nullValue()));

        fake.enqueue(INVENTED_KEY);
        fake.enqueue(GENERATED);
        JsonNode draft = body(mvc.perform(post("/api/v1/projects/{id}/prototype/generate", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spec.screens", hasSize(2)))
                .andExpect(jsonPath("$.spec.screens[0].components[0].type").value("list"))
                .andExpect(jsonPath("$.warnings", hasSize(0))));

        // Drafts are not stored.
        mvc.perform(get("/api/v1/projects/{id}/prototype", projectId))
                .andExpect(jsonPath("$.prototype").value(nullValue()));

        // Rename the model's screen ids by title so they read well, keeping navigation intact.
        ObjectNode spec = (ObjectNode) draft.get("spec").deepCopy();
        String saved = json.writeValueAsString(spec).replace("\"s1\"", "\"browse-vehicles\"").replace("\"s2\"", "\"request-booking\"");
        JsonNode created = body(mvc.perform(put("/api/v1/projects/{id}/prototype", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spec\":" + saved + ",\"version\":null,\"fromAi\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prototype.key").value("PROTOTYPE"))
                .andExpect(jsonPath("$.prototype.data.origin").value("ai"))
                .andExpect(jsonPath("$.prototype.data.prototype.startScreen").value("browse-vehicles")));

        mvc.perform(get("/api/v1/projects"))
                .andExpect(jsonPath("$[?(@.project.id=='" + projectId + "')].counts.screens").value(2));

        fake.enqueue(SCREEN);
        mvc.perform(post("/api/v1/projects/{id}/prototype/screens/{s}/propose", projectId, "request-booking")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"instruction\":\"Show pending status\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("request-booking"))
                .andExpect(jsonPath("$.spec.screens[1].id").value("request-booking"))
                .andExpect(jsonPath("$.spec.screens[1].components[0].type").value("conditional"))
                .andExpect(jsonPath("$.spec.screens[0].components[0].type").value("list"));

        // Stale save is refused.
        mvc.perform(put("/api/v1/projects/{id}/prototype", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spec\":" + saved + ",\"version\":99,\"fromAi\":false}"))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/v1/projects/{id}/prototype", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spec\":" + saved + ",\"version\":" + created.get("prototype").get("version").asLong()
                                + ",\"fromAi\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects/{id}/revisions", projectId))
                .andExpect(jsonPath("$[0].summary").value("Updated prototype"))
                .andExpect(jsonPath("$[1].summary").value("Created prototype from AI draft"));
    }

    @Test
    void rejectsUnsafeOrInvalidSpecs() throws Exception {
        String projectId = projectWithConfirmedRequirement();
        // Unknown component types (e.g. raw HTML) cannot be expressed.
        mvc.perform(put("/api/v1/projects/{id}/prototype", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spec": {"title": "X", "startScreen": "a", "screens": [{"id": "a", "title": "A",
                                  "components": [{"type": "html", "html": "<script>alert(1)</script>"}]}],
                                  "schemaVersion": 1}, "version": null}
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/v1/projects/{id}/prototype", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spec": {"title": "X", "startScreen": "a", "screens": [{"id": "a", "title": "A",
                                  "components": [{"type": "button", "label": "Go",
                                                  "action": {"type": "navigate", "target": "b"}}]}],
                                  "schemaVersion": 1}, "version": null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-prototype"))
                .andExpect(jsonPath("$.errors[0]").value("Screen 'a' navigates to unknown screen 'b'."));
    }

    @Test
    void generationNeedsConfirmedRequirements() throws Exception {
        String projectId = body(mvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Empty\"}"))).get("id").asString();
        mvc.perform(post("/api/v1/projects/{id}/prototype/generate", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    private String projectWithConfirmedRequirement() throws Exception {
        String projectId = body(mvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Vehicle sharing\"}"))).get("id").asString();
        mvc.perform(post("/api/v1/projects/{id}/artifacts", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"requirement\",\"title\":\"Renters request bookings\",\"status\":\"confirmed\"}"))
                .andExpect(status().isCreated());
        return projectId;
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
