package org.openideastudio.revision;

import org.openideastudio.project.Project;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Captures the full committed state of a project as one immutable JSON document.
 * Snapshots are keyed by stable artifact keys, so a restore can recreate rows
 * without depending on surrogate IDs.
 */
@Component
public class SnapshotBuilder {

    public static final int SNAPSHOT_SCHEMA_VERSION = 1;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public SnapshotBuilder(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public JsonNode build(Project project) {
        ObjectNode root = json.createObjectNode();
        root.put("snapshotSchemaVersion", SNAPSHOT_SCHEMA_VERSION);

        ObjectNode p = root.putObject("project");
        p.put("name", project.name());
        p.put("description", project.description());
        p.put("schemaVersion", project.schemaVersion());

        ArrayNode artifacts = root.putArray("artifacts");
        jdbc.sql("""
                SELECT key, type, title, status, data, schema_version
                FROM artifact WHERE project_id = :projectId ORDER BY key
                """)
                .param("projectId", project.id())
                .query(rs -> {
                    ObjectNode a = artifacts.addObject();
                    a.put("key", rs.getString("key"));
                    a.put("type", rs.getString("type"));
                    a.put("title", rs.getString("title"));
                    a.put("status", rs.getString("status"));
                    a.set("data", json.readTree(rs.getString("data")));
                    a.put("schemaVersion", rs.getInt("schema_version"));
                });

        ArrayNode relationships = root.putArray("relationships");
        jdbc.sql("""
                SELECT s.key AS source_key, t.key AS target_key, r.relationship_type
                FROM artifact_relationship r
                JOIN artifact s ON s.id = r.source_artifact_id
                JOIN artifact t ON t.id = r.target_artifact_id
                WHERE r.project_id = :projectId
                ORDER BY s.key, t.key, r.relationship_type
                """)
                .param("projectId", project.id())
                .query(rs -> {
                    ObjectNode r = relationships.addObject();
                    r.put("source", rs.getString("source_key"));
                    r.put("target", rs.getString("target_key"));
                    r.put("type", rs.getString("relationship_type"));
                });

        return root;
    }
}
