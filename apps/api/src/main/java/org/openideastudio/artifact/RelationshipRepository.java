package org.openideastudio.artifact;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Traceability edges between artifacts, addressed by stable keys. */
@Repository
public class RelationshipRepository {

    private final JdbcClient jdbc;

    public RelationshipRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Makes {@code source --type--> target} exist exactly for the given targets. */
    public void replace(UUID projectId, String sourceKey, String type, Collection<String> targetKeys) {
        jdbc.sql("""
                DELETE FROM artifact_relationship r
                USING artifact s
                WHERE r.source_artifact_id = s.id AND s.project_id = :projectId AND s.key = :source
                  AND r.relationship_type = :type
                """)
                .param("projectId", projectId).param("source", sourceKey).param("type", type)
                .update();
        if (targetKeys.isEmpty()) {
            return;
        }
        jdbc.sql("""
                INSERT INTO artifact_relationship (project_id, source_artifact_id, target_artifact_id, relationship_type)
                SELECT :projectId, s.id, t.id, :type
                FROM artifact s JOIN artifact t ON t.project_id = s.project_id
                WHERE s.project_id = :projectId AND s.key = :source AND t.key IN (:targets)
                ON CONFLICT DO NOTHING
                """)
                .param("projectId", projectId).param("source", sourceKey).param("type", type)
                .param("targets", List.copyOf(targetKeys))
                .update();
    }

    /** Keys of artifacts that point at {@code targetKey} with {@code type}. */
    public List<String> sourcesOf(UUID projectId, String targetKey, String type) {
        return jdbc.sql("""
                SELECT s.key FROM artifact_relationship r
                JOIN artifact s ON s.id = r.source_artifact_id
                JOIN artifact t ON t.id = r.target_artifact_id
                WHERE r.project_id = :projectId AND t.key = :target AND r.relationship_type = :type
                ORDER BY s.key
                """)
                .param("projectId", projectId).param("target", targetKey).param("type", type)
                .query(String.class).list();
    }
}
