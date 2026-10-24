package org.openideastudio.project;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @PostMapping
    ResponseEntity<Project> create(@Valid @RequestBody ProjectRequests.Create request) {
        Project project = projects.create(request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + project.id())).body(project);
    }

    @GetMapping
    List<ProjectSummary> list() {
        return projects.list();
    }

    @GetMapping("/{projectId}")
    Project get(@PathVariable UUID projectId) {
        return projects.get(projectId);
    }

    @PatchMapping("/{projectId}")
    Project update(@PathVariable UUID projectId, @Valid @RequestBody ProjectRequests.Update request) {
        return projects.update(projectId, request);
    }

    @DeleteMapping("/{projectId}")
    ResponseEntity<Void> delete(@PathVariable UUID projectId) {
        projects.delete(projectId);
        return ResponseEntity.noContent().build();
    }
}
