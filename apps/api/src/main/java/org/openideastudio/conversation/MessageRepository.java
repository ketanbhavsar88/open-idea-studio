package org.openideastudio.conversation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class MessageRepository {

    private static final String COLUMNS = "id, role, content, metadata, created_at";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public MessageRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public ConversationMessage insert(UUID projectId, String role, String content, JsonNode metadata) {
        return jdbc.sql("""
                INSERT INTO conversation_message (project_id, role, content, metadata)
                VALUES (:projectId, :role, :content, cast(:metadata AS jsonb))
                RETURNING\s""" + COLUMNS)
                .param("projectId", projectId)
                .param("role", role)
                .param("content", content)
                .param("metadata", json.writeValueAsString(metadata))
                .query(this::map)
                .single();
    }

    public List<ConversationMessage> list(UUID projectId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM conversation_message WHERE project_id = :projectId ORDER BY created_at, id")
                .param("projectId", projectId)
                .query(this::map)
                .list();
    }

    /** The most recent {@code limit} messages, oldest first. */
    public List<ConversationMessage> recent(UUID projectId, int limit) {
        return jdbc.sql("SELECT * FROM (SELECT " + COLUMNS + " FROM conversation_message"
                        + " WHERE project_id = :projectId ORDER BY created_at DESC, id DESC LIMIT :limit) recent"
                        + " ORDER BY created_at, id")
                .param("projectId", projectId)
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    public Optional<ConversationMessage> last(UUID projectId) {
        List<ConversationMessage> last = recent(projectId, 1);
        return last.isEmpty() ? Optional.empty() : Optional.of(last.getFirst());
    }

    private ConversationMessage map(ResultSet rs, int rowNum) throws SQLException {
        return new ConversationMessage(
                rs.getObject("id", UUID.class),
                rs.getString("role"),
                rs.getString("content"),
                json.readTree(rs.getString("metadata")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
