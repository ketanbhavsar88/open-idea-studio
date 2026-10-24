package org.openideastudio.revision;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class RevisionRepository {

    private static final String COLUMNS =
            "id, project_id, parent_revision_id, number, summary, created_by, affected_artifacts, created_at";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public RevisionRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Callers must hold the project row lock so revision numbers stay gap-free and unique. */
    public Revision insert(UUID projectId, UUID parentRevisionId, String summary, String createdBy,
                           List<String> affectedArtifacts, JsonNode snapshot) {
        return jdbc.sql("""
                INSERT INTO project_revision
                    (project_id, parent_revision_id, number, summary, created_by, affected_artifacts, snapshot)
                VALUES (:projectId, :parentId,
                        (SELECT coalesce(max(number), 0) + 1 FROM project_revision WHERE project_id = :projectId),
                        :summary, :createdBy, cast(:affected AS jsonb), cast(:snapshot AS jsonb))
                RETURNING\s""" + COLUMNS)
                .param("projectId", projectId)
                .param("parentId", parentRevisionId)
                .param("summary", summary)
                .param("createdBy", createdBy)
                .param("affected", json.writeValueAsString(affectedArtifacts))
                .param("snapshot", json.writeValueAsString(snapshot))
                .query(this::map)
                .single();
    }

    public List<Revision> list(UUID projectId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM project_revision WHERE project_id = :projectId ORDER BY number DESC")
                .param("projectId", projectId)
                .query(this::map)
                .list();
    }

    public Optional<Revision> find(UUID projectId, UUID revisionId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM project_revision WHERE project_id = :projectId AND id = :id")
                .param("projectId", projectId)
                .param("id", revisionId)
                .query(this::map)
                .optional();
    }

    public Optional<JsonNode> findSnapshot(UUID projectId, UUID revisionId) {
        return jdbc.sql("SELECT snapshot FROM project_revision WHERE project_id = :projectId AND id = :id")
                .param("projectId", projectId)
                .param("id", revisionId)
                .query((rs, n) -> json.readTree(rs.getString("snapshot")))
                .optional();
    }

    private Revision map(ResultSet rs, int rowNum) throws SQLException {
        return new Revision(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("parent_revision_id", UUID.class),
                rs.getInt("number"),
                rs.getString("summary"),
                rs.getString("created_by"),
                json.readValue(rs.getString("affected_artifacts"), STRING_LIST),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
