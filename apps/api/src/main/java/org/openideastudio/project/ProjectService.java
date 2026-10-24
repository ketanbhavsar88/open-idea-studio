package org.openideastudio.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.openideastudio.common.ConflictException;
import org.openideastudio.common.NotFoundException;
import org.openideastudio.revision.Revision;
import org.openideastudio.revision.RevisionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final RevisionService revisions;

    public ProjectService(ProjectRepository projects, RevisionService revisions) {
        this.projects = projects;
        this.revisions = revisions;
    }

    @Transactional
    public Project create(ProjectRequests.Create request) {
        String description = request.description() == null ? "" : request.description().strip();
        Project project = projects.insert(request.name().strip(), description);
        Revision initial = revisions.record(project, "Project created", Revision.BY_USER, List.of());
        return projects.update(project.id(), project.name(), project.description(), initial.id());
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> list() {
        return projects.listSummaries();
    }

    @Transactional(readOnly = true)
    public Project get(UUID id) {
        return projects.find(id).orElseThrow(ProjectService::notFound);
    }

    @Transactional
    public Project update(UUID id, ProjectRequests.Update request) {
        Project current = projects.findForUpdate(id).orElseThrow(ProjectService::notFound);
        if (current.version() != request.version()) {
            throw new ConflictException(
                    "The project was changed elsewhere. Reload it and apply your edit again.");
        }

        String name = request.name() == null ? current.name() : request.name().strip();
        String description = request.description() == null ? current.description() : request.description().strip();
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(name, current.name())) {
            changed.add("name");
        }
        if (!Objects.equals(description, current.description())) {
            changed.add("description");
        }
        if (changed.isEmpty()) {
            return current;
        }

        // Write the new state first so the snapshot captures it, then point at the new revision.
        Project edited = projects.update(id, name, description, current.currentRevisionId());
        Revision revision = revisions.record(edited, "Edited project " + String.join(" and ", changed),
                Revision.BY_USER, List.of());
        return projects.update(id, name, description, revision.id());
    }

    /**
     * Locks the project for a content change and checks it exists. Call at the
     * start of any transaction that changes project artifacts.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Project lock(UUID id) {
        return projects.findForUpdate(id).orElseThrow(ProjectService::notFound);
    }

    /** Records a revision for content changed in the current transaction and returns the updated project. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Project commitRevision(Project locked, String summary, String createdBy, List<String> affectedKeys) {
        Revision revision = revisions.record(locked, summary, createdBy, affectedKeys);
        return projects.touch(locked.id(), revision.id());
    }

    @Transactional
    public void delete(UUID id) {
        if (!projects.delete(id)) {
            throw notFound();
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Project not found.");
    }
}
