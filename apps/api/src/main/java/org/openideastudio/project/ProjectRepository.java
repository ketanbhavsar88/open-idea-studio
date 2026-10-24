package org.openideastudio.project;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {

    private static final String COLUMNS =
            "id, name, description, current_revision_id, schema_version, version, created_at, updated_at";

    private final JdbcClient jdbc;

    public ProjectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Project insert(String name, String description) {
        return jdbc.sql("INSERT INTO project (name, description) VALUES (:name, :description) RETURNING " + COLUMNS)
                .param("name", name)
                .param("description", description)
                .query(ProjectRepository::map)
                .single();
    }

    public Optional<Project> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM project WHERE id = :id")
                .param("id", id)
                .query(ProjectRepository::map)
                .optional();
    }

    /** Locks the row for the rest of the transaction so concurrent writers serialize. */
    public Optional<Project> findForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM project WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(ProjectRepository::map)
                .optional();
    }

    public List<ProjectSummary> listSummaries() {
        return jdbc.sql("""
                SELECT p.id, p.name, p.description, p.current_revision_id, p.schema_version, p.version,
                       p.created_at, p.updated_at,
                       count(*) FILTER (WHERE a.type = 'requirement' AND a.status = 'confirmed') AS confirmed_requirements,
                       count(*) FILTER (WHERE a.type = 'question' AND a.status = 'open')         AS open_questions,
                       count(*) FILTER (WHERE a.type = 'flow')                                     AS flows,
                       count(*) FILTER (WHERE a.type = 'screen')                                   AS screens
                FROM project p
                LEFT JOIN artifact a ON a.project_id = p.id
                GROUP BY p.id
                ORDER BY p.updated_at DESC
                """)
                .query((rs, n) -> new ProjectSummary(map(rs, n), new ProjectSummary.Counts(
                        rs.getInt("confirmed_requirements"),
                        rs.getInt("open_questions"),
                        rs.getInt("flows"),
                        rs.getInt("screens"))))
                .list();
    }

    /** Writes new field values and bumps the version; the caller has already checked it. */
    public Project update(UUID id, String name, String description, UUID currentRevisionId) {
        return jdbc.sql("""
                UPDATE project
                SET name = :name, description = :description, current_revision_id = :revisionId,
                    version = version + 1, updated_at = now()
                WHERE id = :id
                RETURNING\s""" + COLUMNS)
                .param("id", id)
                .param("name", name)
                .param("description", description)
                .param("revisionId", currentRevisionId)
                .query(ProjectRepository::map)
                .single();
    }

    /** Records that project content changed: points at the new revision and bumps the version. */
    public Project touch(UUID id, UUID currentRevisionId) {
        return jdbc.sql("""
                UPDATE project SET current_revision_id = :revisionId, version = version + 1, updated_at = now()
                WHERE id = :id
                RETURNING\s""" + COLUMNS)
                .param("id", id)
                .param("revisionId", currentRevisionId)
                .query(ProjectRepository::map)
                .single();
    }

    public boolean delete(UUID id) {
        return jdbc.sql("DELETE FROM project WHERE id = :id").param("id", id).update() > 0;
    }

    private static Project map(ResultSet rs, int rowNum) throws SQLException {
        return new Project(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getObject("current_revision_id", UUID.class),
                rs.getInt("schema_version"),
                rs.getLong("version"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
