package org.openideastudio.artifact;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.json.JsonMapper;

@Repository
public class ArtifactRepository {

    private static final String COLUMNS = "key, type, title, status, data, version, created_at, updated_at";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ArtifactRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Artifact> list(UUID projectId, ArtifactType type) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM artifact WHERE project_id = :projectId"
                        + (type == null ? "" : " AND type = :type") + " ORDER BY created_at, key")
                .param("projectId", projectId)
                .param("type", type == null ? null : type.id())
                .query(this::map)
                .list();
    }

    public Optional<Artifact> find(UUID projectId, String key) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM artifact WHERE project_id = :projectId AND key = :key")
                .param("projectId", projectId)
                .param("key", key)
                .query(this::map)
                .optional();
    }

    public Set<String> keys(UUID projectId) {
        return Set.copyOf(jdbc.sql("SELECT key FROM artifact WHERE project_id = :projectId")
                .param("projectId", projectId)
                .query(String.class)
                .list());
    }

    public Artifact insert(UUID projectId, String key, ArtifactType type, String title, String status,
                           ArtifactData data) {
        return jdbc.sql("""
                INSERT INTO artifact (project_id, key, type, title, status, data)
                VALUES (:projectId, :key, :type, :title, :status, cast(:data AS jsonb))
                RETURNING\s""" + COLUMNS)
                .param("projectId", projectId)
                .param("key", key)
                .param("type", type.id())
                .param("title", title)
                .param("status", status)
                .param("data", json.writeValueAsString(data))
                .query(this::map)
                .single();
    }

    public Artifact update(UUID projectId, String key, String title, String status, ArtifactData data) {
        return jdbc.sql("""
                UPDATE artifact
                SET title = :title, status = :status, data = cast(:data AS jsonb),
                    version = version + 1, updated_at = now()
                WHERE project_id = :projectId AND key = :key
                RETURNING\s""" + COLUMNS)
                .param("projectId", projectId)
                .param("key", key)
                .param("title", title)
                .param("status", status)
                .param("data", json.writeValueAsString(data))
                .query(this::map)
                .single();
    }

    private Artifact map(ResultSet rs, int rowNum) throws SQLException {
        return new Artifact(
                rs.getString("key"),
                ArtifactType.from(rs.getString("type")),
                rs.getString("title"),
                rs.getString("status"),
                json.readValue(rs.getString("data"), ArtifactData.class),
                rs.getLong("version"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
