package org.openideastudio.revision;

import java.util.List;
import java.util.UUID;

import org.openideastudio.common.NotFoundException;
import org.openideastudio.project.Project;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

@Service
public class RevisionService {

    private final RevisionRepository revisions;
    private final SnapshotBuilder snapshots;

    public RevisionService(RevisionRepository revisions, SnapshotBuilder snapshots) {
        this.revisions = revisions;
        this.snapshots = snapshots;
    }

    /**
     * Records the project's current committed state as a new revision. Must run
     * inside the transaction that made the change, after the project row lock is
     * taken, so the snapshot and the change commit or roll back together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Revision record(Project project, String summary, String createdBy, List<String> affectedArtifacts) {
        return revisions.insert(project.id(), project.currentRevisionId(), summary, createdBy,
                affectedArtifacts, snapshots.build(project));
    }

    @Transactional(readOnly = true)
    public List<Revision> list(UUID projectId) {
        return revisions.list(projectId);
    }

    @Transactional(readOnly = true)
    public RevisionDetail get(UUID projectId, UUID revisionId) {
        Revision revision = revisions.find(projectId, revisionId)
                .orElseThrow(() -> new NotFoundException("Revision not found."));
        JsonNode snapshot = revisions.findSnapshot(projectId, revisionId).orElseThrow();
        return new RevisionDetail(revision, snapshot);
    }

    public record RevisionDetail(Revision revision, JsonNode snapshot) {
    }
}
