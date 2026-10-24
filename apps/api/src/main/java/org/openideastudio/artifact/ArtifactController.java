package org.openideastudio.artifact;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/artifacts")
public class ArtifactController {

    private final ArtifactService artifacts;

    public ArtifactController(ArtifactService artifacts) {
        this.artifacts = artifacts;
    }

    @GetMapping
    List<Artifact> list(@PathVariable UUID projectId, @RequestParam(required = false) String type) {
        return artifacts.list(projectId, type);
    }

    @GetMapping("/{key}")
    Artifact get(@PathVariable UUID projectId, @PathVariable String key) {
        return artifacts.get(projectId, key);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Artifact create(@PathVariable UUID projectId, @Valid @RequestBody ArtifactRequests.Create request) {
        return artifacts.create(projectId, request);
    }

    @PatchMapping("/{key}")
    Artifact update(@PathVariable UUID projectId, @PathVariable String key,
                    @Valid @RequestBody ArtifactRequests.Update request) {
        return artifacts.update(projectId, key, request);
    }
}
