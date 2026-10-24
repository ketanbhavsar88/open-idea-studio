package org.openideastudio.conversation;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openideastudio.TestcontainersConfiguration;
import org.openideastudio.ai.FakeProvider;
import org.openideastudio.ai.ModelProvider;
import org.openideastudio.ai.ModelUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {"ai.provider=fake", "ai.min-interval-ms=0"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DiscoveryIntegrationTest {

    static final String FIRST_TURN = """
            {
              "reply": "A peer-to-peer vehicle rental marketplace. Let's pin down booking rules.",
              "ideaSummary": {"summary": "Owners rent idle vehicles to renters for a few hours.",
                              "targetUsers": ["Vehicle owners", "Renters"], "problem": "Private vehicles sit idle."},
              "nextQuestion": {"text": "Does the owner approve each booking?",
                               "why": "It decides whether bookings have a pending state.",
                               "options": ["Owner approves each booking", "Instant booking"]},
              "proposals": [
                {"type": "requirement", "title": "Owners can list a vehicle", "description": "Owners list vehicles with photos and hourly price.",
                 "category": "functional", "priority": "must", "acceptanceCriteria": ["A listing has a price per hour"],
                 "rationale": "Core of the idea.", "impact": null},
                {"type": "assumption", "title": "Renters hold a valid driving licence", "description": null,
                 "category": null, "priority": null, "acceptanceCriteria": [], "rationale": null, "impact": null},
                {"type": "risk", "title": "Vehicle damage disputes", "description": "Who pays for damage?",
                 "category": null, "priority": null, "acceptanceCriteria": [], "rationale": null, "impact": "high"}
              ],
              "answeredQuestions": []
            }
            """;

    static final String SECOND_TURN = """
            {
              "reply": "Got it: owners approve every request.",
              "ideaSummary": null,
              "nextQuestion": null,
              "proposals": [
                {"type": "decision", "title": "Owners approve each booking", "description": "Bookings stay pending until the owner accepts.",
                 "category": "business-rule", "priority": null, "acceptanceCriteria": [], "rationale": "User said so.", "impact": null}
              ],
              "answeredQuestions": [{"key": "Q-DOES-THE-OWNER-APPROVE-EACH-BOOKING", "answer": "Yes, owners approve each booking."},
                                    {"key": "REQ-DOES-NOT-EXIST", "answer": "ignored"}]
            }
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
    void discoveryProposesArtifactsThatOnlyTheUserCanConfirm() throws Exception {
        String projectId = createProject();
        fake.enqueue(FIRST_TURN);

        mvc.perform(post("/api/v1/projects/{id}/messages/start", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userMessage.content").value("Rent unused private vehicles from owners."))
                .andExpect(jsonPath("$.assistantMessage.metadata.question.key").value("Q-DOES-THE-OWNER-APPROVE-EACH-BOOKING"))
                .andExpect(jsonPath("$.createdArtifacts", hasSize(4)));

        mvc.perform(get("/api/v1/projects/{id}/artifacts", projectId))
                .andExpect(jsonPath("$[*].key", containsInAnyOrder("SUMMARY", "REQ-OWNERS-CAN-LIST-A-VEHICLE",
                        "ASM-RENTERS-HOLD-A-VALID-DRIVING-LICENCE", "RISK-VEHICLE-DAMAGE-DISPUTES",
                        "Q-DOES-THE-OWNER-APPROVE-EACH-BOOKING")))
                .andExpect(jsonPath("$[?(@.key=='REQ-OWNERS-CAN-LIST-A-VEHICLE')].status").value("proposed"))
                .andExpect(jsonPath("$[?(@.key=='REQ-OWNERS-CAN-LIST-A-VEHICLE')].data.origin").value("ai"))
                .andExpect(jsonPath("$[?(@.key=='ASM-RENTERS-HOLD-A-VALID-DRIVING-LICENCE')].status").value("unconfirmed"));

        fake.enqueue(SECOND_TURN);
        mvc.perform(post("/api/v1/projects/{id}/messages", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Yes, owners must approve each booking.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdArtifacts[0].key").value("DEC-OWNERS-APPROVE-EACH-BOOKING"))
                .andExpect(jsonPath("$.createdArtifacts[0].status").value("proposed"));

        mvc.perform(get("/api/v1/projects/{id}/artifacts/{key}", projectId, "Q-DOES-THE-OWNER-APPROVE-EACH-BOOKING"))
                .andExpect(jsonPath("$.status").value("answered"))
                .andExpect(jsonPath("$.data.answer").value("Yes, owners approve each booking."));

        // The user confirms the AI's decision.
        JsonNode decision = json.readTree(mvc.perform(
                get("/api/v1/projects/{id}/artifacts/{key}", projectId, "DEC-OWNERS-APPROVE-EACH-BOOKING"))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(patch("/api/v1/projects/{id}/artifacts/{key}", projectId, "DEC-OWNERS-APPROVE-EACH-BOOKING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"confirmed\",\"version\":" + decision.get("version").asLong() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("confirmed"));

        mvc.perform(get("/api/v1/projects/{id}/revisions", projectId))
                .andExpect(jsonPath("$[0].summary").value("Marked DEC-OWNERS-APPROVE-EACH-BOOKING as confirmed"))
                .andExpect(jsonPath("$[0].createdBy").value("user"))
                .andExpect(jsonPath("$[1].createdBy").value("ai-proposal"))
                .andExpect(jsonPath("$[1].affectedArtifacts", hasItem("Q-DOES-THE-OWNER-APPROVE-EACH-BOOKING")));

        mvc.perform(get("/api/v1/projects/{id}/messages", projectId)).andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    void userMessageSurvivesModelFailureAndCanBeRetried() throws Exception {
        String projectId = createProject();
        fake.enqueueFailure(new ModelUnavailableException(ModelUnavailableException.Reason.RATE_LIMITED, "Quota reached."));

        mvc.perform(post("/api/v1/projects/{id}/messages", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hello\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("model-rate-limited"));

        mvc.perform(get("/api/v1/projects/{id}/messages", projectId))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].role").value("user"));

        fake.enqueue(FIRST_TURN);
        mvc.perform(post("/api/v1/projects/{id}/messages/retry", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage.role").value("assistant"));

        mvc.perform(post("/api/v1/projects/{id}/messages/retry", projectId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidModelOutputIsReportedAndNothingIsSaved() throws Exception {
        String projectId = createProject();
        for (int i = 0; i < 3; i++) {
            fake.enqueue("{\"reply\": \"\", \"proposals\": []}");
        }
        mvc.perform(post("/api/v1/projects/{id}/messages", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("invalid-model-output"));
        mvc.perform(get("/api/v1/projects/{id}/artifacts", projectId)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void artifactsCanBeEditedWithoutAnyModel() throws Exception {
        String projectId = createProject();
        fake.enqueueFailure(new ModelUnavailableException(ModelUnavailableException.Reason.UNAVAILABLE, "down"));

        mvc.perform(post("/api/v1/projects/{id}/artifacts", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("type", "requirement", "title", "Renters can search by location",
                                "data", Map.of("category", "functional", "priority", "must")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("REQ-RENTERS-CAN-SEARCH-BY-LOCATION"))
                .andExpect(jsonPath("$.status").value("proposed"))
                .andExpect(jsonPath("$.data.origin").value("user"));

        mvc.perform(patch("/api/v1/projects/{id}/artifacts/{key}", projectId, "REQ-RENTERS-CAN-SEARCH-BY-LOCATION")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"answered\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad-request"));

        mvc.perform(patch("/api/v1/projects/{id}/artifacts/{key}", projectId, "REQ-RENTERS-CAN-SEARCH-BY-LOCATION")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renters search by location\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("REQ-RENTERS-CAN-SEARCH-BY-LOCATION"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(patch("/api/v1/projects/{id}/artifacts/{key}", projectId, "REQ-RENTERS-CAN-SEARCH-BY-LOCATION")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"confirmed\",\"version\":0}"))
                .andExpect(status().isConflict());
    }

    private String createProject() throws Exception {
        String response = mvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vehicle sharing\",\"description\":\"Rent unused private vehicles from owners.\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asString();
    }
}
