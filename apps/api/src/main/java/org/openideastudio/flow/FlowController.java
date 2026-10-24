package org.openideastudio.flow;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.openideastudio.artifact.Artifact;
import org.openideastudio.changeset.ChangeSet;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class FlowController {

    private final FlowService flows;

    public FlowController(FlowService flows) {
        this.flows = flows;
    }

    @GetMapping("/flows")
    List<Artifact> list(@PathVariable UUID projectId) {
        return flows.list(projectId);
    }

    @GetMapping("/flows/{key}")
    Artifact get(@PathVariable UUID projectId, @PathVariable String key) {
        return flows.get(projectId, key);
    }

    @PostMapping("/flows")
    @ResponseStatus(HttpStatus.CREATED)
    Artifact create(@PathVariable UUID projectId, @Valid @RequestBody CreateRequest request) {
        return flows.create(projectId, request.title());
    }

    @PutMapping("/flows/{key}")
    FlowService.SaveResult save(@PathVariable UUID projectId, @PathVariable String key,
                                @Valid @RequestBody SaveRequest request) {
        return flows.save(projectId, key, request.title(), request.graph(), request.version());
    }

    @PostMapping("/flows/generate")
    @ResponseStatus(HttpStatus.CREATED)
    FlowService.SaveResult generate(@PathVariable UUID projectId, @Valid @RequestBody GenerateRequest request) {
        return flows.generate(projectId, request.title(), request.requirementKeys());
    }

    @PostMapping("/flows/{key}/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    FlowService.Proposal propose(@PathVariable UUID projectId, @PathVariable String key,
                                 @Valid @RequestBody ProposeRequest request) {
        return flows.propose(projectId, key, request.instruction().strip());
    }

    @GetMapping("/flows/{key}/proposals")
    List<ChangeSet> pending(@PathVariable UUID projectId, @PathVariable String key) {
        return flows.pending(projectId, key);
    }

    @GetMapping("/changesets/{changeSetId}")
    FlowService.Proposal changeSet(@PathVariable UUID projectId, @PathVariable UUID changeSetId) {
        return flows.getProposal(projectId, changeSetId);
    }

    @PostMapping("/changesets/{changeSetId}/apply")
    FlowService.ApplyResult apply(@PathVariable UUID projectId, @PathVariable UUID changeSetId) {
        return flows.apply(projectId, changeSetId);
    }

    @PostMapping("/changesets/{changeSetId}/reject")
    ChangeSet reject(@PathVariable UUID projectId, @PathVariable UUID changeSetId) {
        return flows.reject(projectId, changeSetId);
    }

    record CreateRequest(@NotBlank @Size(max = 200) String title) {
    }

    record SaveRequest(@Size(max = 200) String title, @NotNull @Valid FlowGraph graph, @NotNull Long version) {
    }

    record GenerateRequest(@Size(max = 200) String title, @Size(max = 50) List<String> requirementKeys) {
    }

    record ProposeRequest(@NotBlank @Size(max = 2000) String instruction) {
    }
}
