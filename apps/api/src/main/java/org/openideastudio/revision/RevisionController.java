package org.openideastudio.revision;

import java.util.List;
import java.util.UUID;

import org.openideastudio.project.ProjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/revisions")
public class RevisionController {

    private final RevisionService revisions;
    private final ProjectService projects;

    public RevisionController(RevisionService revisions, ProjectService projects) {
        this.revisions = revisions;
        this.projects = projects;
    }

    @GetMapping
    List<Revision> list(@PathVariable UUID projectId) {
        projects.get(projectId);
        return revisions.list(projectId);
    }

    @GetMapping("/{revisionId}")
    RevisionService.RevisionDetail get(@PathVariable UUID projectId, @PathVariable UUID revisionId) {
        return revisions.get(projectId, revisionId);
    }
}
