package org.openideastudio.artifact;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.openideastudio.common.BadRequestException;
import org.openideastudio.common.ConflictException;
import org.openideastudio.common.NotFoundException;
import org.openideastudio.project.Project;
import org.openideastudio.project.ProjectService;
import org.openideastudio.revision.Revision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtifactService {

    private final ArtifactRepository artifacts;
    private final ProjectService projects;

    public ArtifactService(ArtifactRepository artifacts, ProjectService projects) {
        this.artifacts = artifacts;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<Artifact> list(UUID projectId, String type) {
        projects.get(projectId);
        return artifacts.list(projectId, type == null ? null : parseType(type));
    }

    @Transactional(readOnly = true)
    public Artifact get(UUID projectId, String key) {
        return artifacts.find(projectId, key).orElseThrow(() -> new NotFoundException("Artifact not found."));
    }

    /** A user creates an artifact directly; this works without any model. */
    @Transactional
    public Artifact create(UUID projectId, ArtifactRequests.Create request) {
        ArtifactType type = parseType(request.type());
        if (type == ArtifactType.SUMMARY) {
            throw new BadRequestException("The summary is maintained by discovery; edit it instead.");
        }
        if (type == ArtifactType.FLOW) {
            throw new BadRequestException("Create flows with the flows API so the graph is validated.");
        }
        String status = request.status() == null ? type.initialStatus() : request.status();
        requireStatus(type, status);
        Project project = projects.lock(projectId);
        ArtifactData data = (request.data() == null ? ArtifactData.empty() : request.data()).withOrigin("user");
        String key = ArtifactKeys.generate(type, request.title(), artifacts.keys(projectId));
        Artifact created = artifacts.insert(projectId, key, type, request.title().strip(), status, data);
        projects.commitRevision(project, "Added " + type.id() + " " + key, Revision.BY_USER, List.of(key));
        return created;
    }

    @Transactional
    public Artifact update(UUID projectId, String key, ArtifactRequests.Update request) {
        Project project = projects.lock(projectId);
        Artifact current = get(projectId, key);
        if (current.version() != request.version()) {
            throw new ConflictException("This item was changed elsewhere. Reload it and apply your edit again.");
        }
        if (current.type() == ArtifactType.FLOW && request.data() != null) {
            throw new BadRequestException("Edit a flow's graph with PUT /flows/{key} so it is validated.");
        }
        String status = request.status() == null ? current.status() : request.status();
        requireStatus(current.type(), status);
        String title = request.title() == null ? current.title() : request.title().strip();
        ArtifactData data = request.data() == null ? current.data() : request.data().withOrigin(current.data().origin());
        if (current.type() == ArtifactType.SUMMARY && request.data() != null) {
            status = "edited";
        }

        Artifact updated = artifacts.update(projectId, key, title, status, data);
        String summary = !status.equals(current.status())
                ? "Marked " + key + " as " + status
                : "Edited " + key;
        projects.commitRevision(project, summary, Revision.BY_USER, List.of(key));
        return updated;
    }

    /**
     * Stores validated AI proposals with backend-generated keys and non-confirmed
     * statuses. Must run inside a transaction that already holds the project lock.
     * Proposals whose title duplicates an existing artifact of the same type are skipped.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Artifact> addProposals(UUID projectId, List<NewArtifact> proposals) {
        Set<String> taken = new HashSet<>(artifacts.keys(projectId));
        Set<String> existingTitles = new HashSet<>();
        artifacts.list(projectId, null).forEach(a -> existingTitles.add(titleKey(a.type(), a.title())));

        List<Artifact> created = new ArrayList<>();
        for (NewArtifact p : proposals) {
            if (!existingTitles.add(titleKey(p.type(), p.title()))) {
                continue;
            }
            String key = ArtifactKeys.generate(p.type(), p.title(), taken);
            taken.add(key);
            created.add(artifacts.insert(projectId, key, p.type(), p.title(), p.type().initialStatus(),
                    p.data().withOrigin("ai")));
        }
        return created;
    }

    /**
     * Replaces the AI idea summary, unless the user has edited it. Returns the key
     * if it changed. Must run inside a transaction holding the project lock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String upsertSummary(UUID projectId, String title, ArtifactData data) {
        String key = ArtifactType.SUMMARY.prefix();
        var existing = artifacts.find(projectId, key);
        if (existing.isEmpty()) {
            artifacts.insert(projectId, key, ArtifactType.SUMMARY, title, "ai-generated", data.withOrigin("ai"));
            return key;
        }
        if (existing.get().status().equals("edited")) {
            return null;
        }
        artifacts.update(projectId, key, title, "ai-generated", data.withOrigin("ai"));
        return key;
    }

    /** A proposed artifact before it has a key. */
    public record NewArtifact(ArtifactType type, String title, ArtifactData data) {
    }

    private static String titleKey(ArtifactType type, String title) {
        return type.id() + ":" + title.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private static ArtifactType parseType(String type) {
        try {
            return ArtifactType.from(type);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private static void requireStatus(ArtifactType type, String status) {
        if (!type.allows(status)) {
            throw new BadRequestException("Status '" + status + "' is not valid for a " + type.id()
                    + ". Allowed: " + String.join(", ", type.statuses()) + ".");
        }
    }
}
