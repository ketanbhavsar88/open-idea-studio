package org.openideastudio.project;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.openideastudio.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProjectApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Test
    void createReadUpdateDeleteWithRevisions() throws Exception {
        JsonNode created = createProject("Vehicle sharing",
                "People rent unused private vehicles from owners for a few hours.");
        String id = created.get("id").asString();

        mvc.perform(get("/api/v1/projects/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Vehicle sharing"))
                .andExpect(jsonPath("$.currentRevisionId").value(created.get("currentRevisionId").asString()));

        mvc.perform(patch("/api/v1/projects/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Car sharing\",\"version\":" + created.get("version").asLong() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Car sharing"));

        mvc.perform(get("/api/v1/projects/{id}/revisions", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].number").value(2))
                .andExpect(jsonPath("$[0].summary").value("Edited project name"))
                .andExpect(jsonPath("$[1].summary").value("Project created"));

        String firstRevision = created.get("currentRevisionId").asString();
        mvc.perform(get("/api/v1/projects/{id}/revisions/{rev}", id, firstRevision))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.project.name").value("Vehicle sharing"));

        mvc.perform(delete("/api/v1/projects/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/projects/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not-found"));
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        JsonNode created = createProject("Stale test", "");
        String id = created.get("id").asString();
        long version = created.get("version").asLong();

        mvc.perform(patch("/api/v1/projects/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"First\",\"version\":" + version + "}"))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/projects/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Second\",\"version\":" + version + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-version"));
    }

    @Test
    void blankNameIsRejected() throws Exception {
        mvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());
    }

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    private JsonNode createProject(String name, String description) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("name", name, "description", description));
        String response = mvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }
}
