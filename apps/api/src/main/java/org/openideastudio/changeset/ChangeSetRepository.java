package org.openideastudio.changeset;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.openideastudio.flow.FlowOperation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class ChangeSetRepository {

    private static final String COLUMNS = """
            id, kind, base_revision_id, target_artifact_key, target_artifact_version, instruction, summary,
            operations, warnings, state, applied_revision_id, created_at, applied_at""";
    private static final TypeReference<List<FlowOperation>> OPERATIONS = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ChangeSetRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public ChangeSet insert(UUID projectId, String kind, UUID baseRevisionId, String targetKey, long targetVersion,
                            String instruction, String summary, List<FlowOperation> operations,
                            List<String> warnings) {
        return jdbc.sql("""
                INSERT INTO proposed_change_set (project_id, kind, base_revision_id, target_artifact_key,
                    target_artifact_version, instruction, summary, operations, warnings, validation_status)
                VALUES (:projectId, :kind, :base, :key, :version, :instruction, :summary,
                    cast(:operations AS jsonb), cast(:warnings AS jsonb), 'valid')
                RETURNING\s""" + COLUMNS)
                .param("projectId", projectId).param("kind", kind).param("base", baseRevisionId)
                .param("key", targetKey).param("version", targetVersion).param("instruction", instruction)
                .param("summary", summary)
                .param("operations", json.writeValueAsString(operations))
                .param("warnings", json.writeValueAsString(warnings))
                .query(this::map).single();
    }

    public Optional<ChangeSet> find(UUID projectId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM proposed_change_set WHERE project_id = :projectId AND id = :id")
                .param("projectId", projectId).param("id", id)
                .query(this::map).optional();
    }

    public List<ChangeSet> listPending(UUID projectId, String targetKey) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM proposed_change_set WHERE project_id = :projectId"
                        + " AND target_artifact_key = :key AND state = 'pending' ORDER BY created_at DESC")
                .param("projectId", projectId).param("key", targetKey)
                .query(this::map).list();
    }

    public ChangeSet markState(UUID id, String state, UUID appliedRevisionId) {
        return jdbc.sql("""
                UPDATE proposed_change_set
                SET state = :state, applied_revision_id = :revision,
                    applied_at = CASE WHEN :state = 'applied' THEN now() ELSE applied_at END
                WHERE id = :id
                RETURNING\s""" + COLUMNS)
                .param("id", id).param("state", state).param("revision", appliedRevisionId)
                .query(this::map).single();
    }

    private ChangeSet map(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime appliedAt = rs.getObject("applied_at", OffsetDateTime.class);
        return new ChangeSet(
                rs.getObject("id", UUID.class),
                rs.getString("kind"),
                rs.getObject("base_revision_id", UUID.class),
                rs.getString("target_artifact_key"),
                rs.getLong("target_artifact_version"),
                rs.getString("instruction"),
                rs.getString("summary"),
                json.readValue(rs.getString("operations"), OPERATIONS),
                json.readValue(rs.getString("warnings"), STRINGS),
                rs.getString("state"),
                rs.getObject("applied_revision_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                appliedAt == null ? null : appliedAt.toInstant());
    }
}
