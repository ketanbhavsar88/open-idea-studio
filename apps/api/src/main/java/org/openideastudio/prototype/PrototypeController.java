package org.openideastudio.prototype;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.openideastudio.artifact.Artifact;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/prototype")
public class PrototypeController {

    private final PrototypeService prototypes;

    public PrototypeController(PrototypeService prototypes) {
        this.prototypes = prototypes;
    }

    /** {@code prototype} is null when none has been saved yet. */
    @GetMapping
    View get(@PathVariable UUID projectId) {
        return new View(prototypes.get(projectId).orElse(null));
    }

    /** Create (version null) or replace the prototype. */
    @PutMapping
    PrototypeService.SaveResult save(@PathVariable UUID projectId, @Valid @RequestBody SaveRequest request) {
        return prototypes.save(projectId, request.spec(), request.version(), Boolean.TRUE.equals(request.fromAi()));
    }

    /** AI draft of a whole prototype; not saved. */
    @PostMapping("/generate")
    PrototypeService.Draft generate(@PathVariable UUID projectId, @Valid @RequestBody GenerateRequest request) {
        return prototypes.generate(projectId, request.instruction());
    }

    /** AI draft of one redesigned screen; not saved. */
    @PostMapping("/screens/{screenId}/propose")
    PrototypeService.Draft proposeScreen(@PathVariable UUID projectId, @PathVariable String screenId,
                                         @Valid @RequestBody ScreenRequest request) {
        return prototypes.proposeScreen(projectId, screenId, request.instruction().strip());
    }

    record View(Artifact prototype) {
    }

    record SaveRequest(@NotNull @Valid PrototypeSpec spec, Long version, Boolean fromAi) {
    }

    record GenerateRequest(@Size(max = 2000) String instruction) {
    }

    record ScreenRequest(@NotBlank @Size(max = 2000) String instruction) {
    }
}
