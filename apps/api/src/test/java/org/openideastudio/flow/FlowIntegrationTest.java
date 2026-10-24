package org.openideastudio.flow;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(properties = {"ai.provider=fake", "ai.min-interval-ms=0"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FlowIntegrationTest {

    static final String GENERATED = """
            {"title": "Booking flow", "description": "Renter books a vehicle.",
             "nodes": [
               {"ref": "n1", "type": "start", "label": "Renter opens listing", "description": null, "requirementKeys": []},
               {"ref": "n2", "type": "action", "label": "Request booking", "description": null, "requirementKeys": ["REQ-RENTERS-REQUEST-BOOKINGS"]},
               {"ref": "n3", "type": "decision", "label": "Owner approves?", "description": null, "requirementKeys": ["REQ-OWNERS-APPROVE-BOOKINGS"]},
               {"ref": "n4", "type": "end", "label": "Booking confirmed", "description": null, "requirementKeys": []},
               {"ref": "n5", "type": "end", "label": "Booking rejected", "description": null, "requirementKeys": []}
             ],
             "edges": [
               {"from": "n1", "to": "n2", "label": null},
               {"from": "n2", "to": "n3", "label": null},
               {"from": "n3", "to": "n4", "label": "Approved"},
               {"from": "n3", "to": "n5", "label": "Rejected"}
             ]}
            """;

    static final String BAD_KEY = GENERATED.replace("REQ-OWNERS-APPROVE-BOOKINGS", "REQ-INVENTED");

    static final String PROPOSAL = """
            {"summary": "Add a payment step after approval.",
             "operations": [
               {"op": "removeEdge", "edgeId": "e-owner-approves-booking-confirmed"},
               {"op": "addNode", "ref": "new1", "nodeType": "action", "label": "Pay deposit", "requirementKeys": []},
               {"op": "addEdge", "from": "node-owner-approves", "to": "new1", "edgeLabel": "Approved"},
               {"op": "addEdge", "from": "new1", "to": "node-booking-confirmed"}
             ],
             "warnings": []}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    ModelProvider provider;

    @Autowired
    JdbcClient jdbc;

    FakeProvider fake;

    @BeforeEach
    void setUp() {
        fake = (FakeProvider) provider;
        fake.reset();
    }

    @Test
    void generateEditProposeApply() throws Exception {
        String projectId = projectWithConfirmedRequirements();

        // The first reply invents a requirement key, so the gateway retries with the error.
        fake.enqueue(BAD_KEY);
        fake.enqueue(GENERATED);
        JsonNode generated = body(mvc.perform(post("/api/v1/projects/{id}/flows/generate", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flow.key").value("FLOW-BOOKING-FLOW"))
                .andExpect(jsonPath("$.flow.status").value("draft"))
                .andExpect(jsonPath("$.flow.data.graph.nodes", hasSize(5)))
                .andExpect(jsonPath("$.warnings", hasSize(0))));
        JsonNode flow = generated.get("flow");

        assertRelationships(projectId, 2);

        // Direct canvas edit: move a node and rename it; ids stay stable.
        ObjectNode graph = (ObjectNode) flow.get("data").get("graph").deepCopy();
        ObjectNode request = (ObjectNode) graph.get("nodes").get(1);
        request.put("label", "Send booking request");
        ((ObjectNode) request.get("position")).put("x", 500);
        JsonNode saved = body(mvc.perform(put("/api/v1/projects/{id}/flows/{key}", projectId, "FLOW-BOOKING-FLOW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("graph", graph, "version", flow.get("version").asLong()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flow.data.graph.nodes[1].id").value("node-request-booking"))
                .andExpect(jsonPath("$.flow.data.graph.nodes[1].label").value("Send booking request"))
                .andExpect(jsonPath("$.flow.data.graph.nodes[1].position.x").value(500.0)));

        // AI proposal is stored, not applied.
        fake.enqueue(PROPOSAL);
        JsonNode proposal = body(mvc.perform(post("/api/v1/projects/{id}/flows/{key}/proposals", projectId, "FLOW-BOOKING-FLOW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":\"Add a payment step after approval\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.changeSet.state").value("pending"))
                .andExpect(jsonPath("$.changeSet.operations[1].nodeId").value("node-pay-deposit"))
                .andExpect(jsonPath("$.preview.nodes", hasSize(6))));
        mvc.perform(get("/api/v1/projects/{id}/flows/{key}", projectId, "FLOW-BOOKING-FLOW"))
                .andExpect(jsonPath("$.data.graph.nodes", hasSize(5)));

        String changeSetId = proposal.get("changeSet").get("id").asString();
        mvc.perform(post("/api/v1/projects/{id}/changesets/{cs}/apply", projectId, changeSetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeSet.state").value("applied"))
                .andExpect(jsonPath("$.flow.data.graph.nodes", hasSize(6)))
                // The user's earlier canvas edit survives the AI change.
                .andExpect(jsonPath("$.flow.data.graph.nodes[1].label").value("Send booking request"))
                .andExpect(jsonPath("$.flow.data.graph.nodes[1].position.x").value(500.0));

        mvc.perform(post("/api/v1/projects/{id}/changesets/{cs}/apply", projectId, changeSetId))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/projects/{id}/revisions", projectId))
                .andExpect(jsonPath("$[0].summary").value(
                        "Applied AI change to FLOW-BOOKING-FLOW: Add a payment step after approval."))
                .andExpect(jsonPath("$[1].summary").value("Edited flow FLOW-BOOKING-FLOW"))
                .andExpect(jsonPath("$[2].summary").value("Generated flow FLOW-BOOKING-FLOW"));
        mvc.perform(get("/api/v1/projects/{id}/revisions/{rev}", projectId, revisionId(projectId)))
                .andExpect(jsonPath("$.snapshot.relationships[*].type", hasItem("implements")));
    }

    @Test
    void proposalBecomesStaleWhenTheFlowChanges() throws Exception {
        String projectId = projectWithConfirmedRequirements();
        fake.enqueue(GENERATED);
        JsonNode flow = body(mvc.perform(post("/api/v1/projects/{id}/flows/generate", projectId)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))).get("flow");

        fake.enqueue(PROPOSAL);
        String changeSetId = body(mvc.perform(post("/api/v1/projects/{id}/flows/{key}/proposals", projectId, "FLOW-BOOKING-FLOW")
                .contentType(MediaType.APPLICATION_JSON).content("{\"instruction\":\"Add payment\"}")))
                .get("changeSet").get("id").asString();

        mvc.perform(put("/api/v1/projects/{id}/flows/{key}", projectId, "FLOW-BOOKING-FLOW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Booking", "graph", flow.get("data").get("graph"),
                                "version", flow.get("version").asLong()))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/projects/{id}/changesets/{cs}/apply", projectId, changeSetId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-version"));
        mvc.perform(get("/api/v1/projects/{id}/changesets/{cs}", projectId, changeSetId))
                .andExpect(jsonPath("$.changeSet.state").value("stale"));
    }

    @Test
    void rejectsInvalidGraphsAndProposalsCanBeRejected() throws Exception {
        String projectId = projectWithConfirmedRequirements();
        JsonNode flow = body(mvc.perform(post("/api/v1/projects/{id}/flows", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Onboarding\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("FLOW-ONBOARDING"))
                .andExpect(jsonPath("$.data.graph.nodes", hasSize(2))));

        ObjectNode graph = (ObjectNode) flow.get("data").get("graph").deepCopy();
        ArrayNode edges = (ArrayNode) graph.get("edges");
        edges.addObject().put("id", "e-bad").put("source", "node-start").put("target", "node-nowhere");
        mvc.perform(put("/api/v1/projects/{id}/flows/{key}", projectId, "FLOW-ONBOARDING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("graph", graph, "version", flow.get("version").asLong()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-graph"))
                .andExpect(jsonPath("$.errors[0]").value("Edge 'e-bad' ends at unknown node 'node-nowhere'."));

        // Graphs cannot be changed through the generic artifact API.
        mvc.perform(patch("/api/v1/projects/{id}/artifacts/{key}", projectId, "FLOW-ONBOARDING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"description\":\"x\"},\"version\":0}"))
                .andExpect(status().isBadRequest());

        fake.enqueue("""
                {"summary": "Connect start to end.", "operations": [{"op": "addEdge", "from": "node-start", "to": "node-end"}],
                 "warnings": []}
                """);
        String changeSetId = body(mvc.perform(post("/api/v1/projects/{id}/flows/{key}/proposals", projectId, "FLOW-ONBOARDING")
                .contentType(MediaType.APPLICATION_JSON).content("{\"instruction\":\"Connect them\"}")))
                .get("changeSet").get("id").asString();
        mvc.perform(post("/api/v1/projects/{id}/changesets/{cs}/reject", projectId, changeSetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("rejected"));
        mvc.perform(get("/api/v1/projects/{id}/flows/{key}", projectId, "FLOW-ONBOARDING"))
                .andExpect(jsonPath("$.data.graph.edges", hasSize(0)));
    }

    @Test
    void generationNeedsConfirmedRequirements() throws Exception {
        String projectId = body(mvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Empty\"}"))).get("id").asString();
        mvc.perform(post("/api/v1/projects/{id}/flows/generate", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Confirm at least one requirement before generating a flow."));
    }

    private String projectWithConfirmedRequirements() throws Exception {
        String projectId = body(mvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Vehicle sharing\"}"))).get("id").asString();
        for (String title : new String[] {"Renters request bookings", "Owners approve bookings"}) {
            mvc.perform(post("/api/v1/projects/{id}/artifacts", projectId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("type", "requirement", "title", title,
                                    "status", "confirmed"))))
                    .andExpect(status().isCreated());
        }
        return projectId;
    }

    private void assertRelationships(String projectId, int expected) {
        Integer count = jdbc.sql("SELECT count(*) FROM artifact_relationship WHERE project_id = cast(:id AS uuid)"
                        + " AND relationship_type = 'implements'")
                .param("id", projectId).query(Integer.class).single();
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(expected);
    }

    private String revisionId(String projectId) throws Exception {
        return body(mvc.perform(get("/api/v1/projects/{id}", projectId))).get("currentRevisionId").asString();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
