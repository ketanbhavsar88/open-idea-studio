package org.openideastudio.prototype;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.openideastudio.ai.ModelGateway;
import org.openideastudio.ai.ModelMessage;
import org.openideastudio.artifact.Artifact;
import org.openideastudio.artifact.ArtifactData;
import org.openideastudio.artifact.ArtifactRepository;
import org.openideastudio.artifact.ArtifactType;
import org.openideastudio.artifact.RelationshipRepository;
import org.openideastudio.common.BadRequestException;
import org.openideastudio.common.ConflictException;
import org.openideastudio.common.NotFoundException;
import org.openideastudio.common.ValidationFailedException;
import org.openideastudio.project.Project;
import org.openideastudio.project.ProjectService;
import org.openideastudio.revision.Revision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The project's single prototype. AI generation and screen regeneration return
 * drafts only; nothing is stored until the user saves (design doc §5.6, §12).
 */
@Service
public class PrototypeService {

    static final String KEY = ArtifactType.PROTOTYPE.prefix();
    static final String IMPLEMENTS = "implements";

    private final ArtifactRepository artifacts;
    private final RelationshipRepository relationships;
    private final ProjectService projects;
    private final ModelGateway model;
    private final TransactionTemplate tx;

    public PrototypeService(ArtifactRepository artifacts, RelationshipRepository relationships,
                            ProjectService projects, ModelGateway model, TransactionTemplate tx) {
        this.artifacts = artifacts;
        this.relationships = relationships;
        this.projects = projects;
        this.model = model;
        this.tx = tx;
    }

    public Optional<Artifact> get(UUID projectId) {
        projects.get(projectId);
        return artifacts.find(projectId, KEY);
    }

    /** Creates ({@code version == null}) or replaces the prototype after validating it. */
    public SaveResult save(UUID projectId, PrototypeSpec spec, Long expectedVersion, boolean fromAi) {
        return tx.execute(s -> {
            Project project = projects.lock(projectId);
            Optional<Artifact> current = artifacts.find(projectId, KEY);
            if (current.isPresent() && (expectedVersion == null || current.get().version() != expectedVersion)) {
                throw new ConflictException("The prototype was changed elsewhere. Reload it and try again.");
            }
            if (current.isEmpty() && expectedVersion != null) {
                throw new ConflictException("The prototype was deleted or never existed. Reload and try again.");
            }
            PrototypeSpec normalized = normalize(spec);
            PrototypeValidator.Result check = requireValid(projectId, normalized);
            ArtifactData data = new ArtifactData(null, null, null, null, null, null, null, null, null, null, null,
                    fromAi ? "ai" : "user", null, normalized);
            Artifact saved = current.isPresent()
                    ? artifacts.update(projectId, KEY, normalized.title(), current.get().status(), data)
                    : artifacts.insert(projectId, KEY, ArtifactType.PROTOTYPE, normalized.title(), "draft", data);
            relationships.replace(projectId, KEY, IMPLEMENTS, linkedRequirements(normalized));
            String summary = (current.isPresent() ? "Updated prototype" : "Created prototype")
                    + (fromAi ? " from AI draft" : "");
            projects.commitRevision(project, summary, fromAi ? Revision.BY_AI : Revision.BY_USER, List.of(KEY));
            return new SaveResult(saved, check.warnings());
        });
    }

    /** Asks the model for a full prototype; returns an unsaved draft. */
    public Draft generate(UUID projectId, String instruction) {
        Context ctx = context(projectId);
        if (ctx.requirements().isEmpty()) {
            throw new BadRequestException("Confirm at least one requirement before generating a prototype.");
        }
        PrototypeSpec existing = ctx.existing();
        String prompt = PrototypePrompts.context(ctx.project().name(), ctx.summary(), ctx.requirements(),
                ctx.decisions(), ctx.flows()) + PrototypePrompts.existingScreens(existing)
                + (instruction == null || instruction.isBlank() ? "" : "\nAdditional instruction: " + instruction);

        PrototypeSpec raw = model.generate("generate-prototype", PrototypePrompts.GENERATE,
                List.of(ModelMessage.user(prompt)), PrototypeSpec.class,
                spec -> PrototypeValidator.validate(spec, ctx.requirementKeys()).errors());

        PrototypeSpec spec = normalize(stabilizeIds(raw, existing));
        PrototypeValidator.Result check = PrototypeValidator.validate(spec, ctx.requirementKeys());
        if (!check.valid()) {
            throw new ValidationFailedException("invalid-prototype", "The AI draft is not valid.", check.errors());
        }
        return new Draft(spec, check.warnings(), null);
    }

    /** Asks the model to redesign one screen; returns the whole spec with that screen replaced, unsaved. */
    public Draft proposeScreen(UUID projectId, String screenId, String instruction) {
        Context ctx = context(projectId);
        PrototypeSpec existing = Optional.ofNullable(ctx.existing())
                .orElseThrow(() -> new NotFoundException("There is no prototype yet."));
        PrototypeSpec.Screen screen = existing.screens().stream().filter(sc -> sc.id().equals(screenId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Screen not found."));

        String prompt = PrototypePrompts.context(ctx.project().name(), ctx.summary(), ctx.requirements(),
                ctx.decisions(), List.of()) + PrototypePrompts.existingScreens(existing)
                + "\nScreen to redesign: " + screen.id() + " (\"" + screen.title() + "\")\n"
                + "Current components (JSON): " + model.json().writeValueAsString(screen.components())
                + "\nInstruction: " + instruction;

        PrototypeSpec.Screen proposed = model.generate("propose-screen", PrototypePrompts.PROPOSE_SCREEN,
                List.of(ModelMessage.user(prompt)), PrototypeSpec.Screen.class,
                sc -> PrototypeValidator.validate(replace(existing, screenId, sc), ctx.requirementKeys()).errors());

        PrototypeSpec spec = normalize(replace(existing, screenId, proposed));
        PrototypeValidator.Result check = PrototypeValidator.validate(spec, ctx.requirementKeys());
        if (!check.valid()) {
            throw new ValidationFailedException("invalid-prototype", "The AI draft is not valid.", check.errors());
        }
        return new Draft(spec, check.warnings(), screenId);
    }

    public record SaveResult(Artifact prototype, List<String> warnings) {
    }

    /** @param screenId set when only one screen was redesigned */
    public record Draft(PrototypeSpec spec, List<String> warnings, String screenId) {
    }

    // --- internals ---------------------------------------------------------------------------

    private record Context(Project project, Artifact summary, List<Artifact> requirements, List<Artifact> decisions,
                           List<Artifact> flows, PrototypeSpec existing, Set<String> requirementKeys) {
    }

    private Context context(UUID projectId) {
        Project project = projects.get(projectId);
        List<Artifact> all = artifacts.list(projectId, null);
        List<Artifact> requirements = all.stream()
                .filter(a -> a.type() == ArtifactType.REQUIREMENT && a.status().equals("confirmed")).toList();
        List<Artifact> decisions = all.stream()
                .filter(a -> a.type() == ArtifactType.DECISION && a.status().equals("confirmed")).toList();
        List<Artifact> flows = all.stream()
                .filter(a -> a.type() == ArtifactType.FLOW && !a.status().equals("archived")).toList();
        Artifact summary = all.stream().filter(a -> a.type() == ArtifactType.SUMMARY).findFirst().orElse(null);
        PrototypeSpec existing = all.stream().filter(a -> a.type() == ArtifactType.PROTOTYPE).findFirst()
                .map(a -> a.data().prototype()).orElse(null);
        Set<String> keys = new HashSet<>(requirements.stream().map(Artifact::key).toList());
        return new Context(project, summary, requirements, decisions, flows, existing, keys);
    }

    private PrototypeValidator.Result requireValid(UUID projectId, PrototypeSpec spec) {
        Set<String> keys = new HashSet<>(artifacts.list(projectId, ArtifactType.REQUIREMENT).stream()
                .map(Artifact::key).toList());
        PrototypeValidator.Result check = PrototypeValidator.validate(spec, keys);
        if (!check.valid()) {
            throw new ValidationFailedException("invalid-prototype", "The prototype is not valid.", check.errors());
        }
        return check;
    }

    private static PrototypeSpec replace(PrototypeSpec spec, String screenId, PrototypeSpec.Screen proposed) {
        List<PrototypeSpec.Screen> screens = spec.screens().stream()
                .map(sc -> sc.id().equals(screenId)
                        ? new PrototypeSpec.Screen(screenId, proposed.title(), proposed.description(),
                                proposed.requirementKeys(), proposed.components())
                        : sc)
                .toList();
        return new PrototypeSpec(spec.title(), spec.startScreen(), spec.initialState(), screens, spec.schemaVersion());
    }

    /**
     * Keeps screen IDs stable across regeneration: a generated screen reuses the
     * ID of an existing screen with the same ID or title; navigation is rewritten to match.
     */
    static PrototypeSpec stabilizeIds(PrototypeSpec raw, PrototypeSpec existing) {
        Map<String, String> byTitle = new HashMap<>();
        Set<String> existingIds = new HashSet<>();
        if (existing != null) {
            existing.screens().forEach(sc -> {
                byTitle.put(sc.title().strip().toLowerCase(Locale.ROOT), sc.id());
                existingIds.add(sc.id());
            });
        }
        Map<String, String> mapping = new HashMap<>();
        Set<String> used = new HashSet<>();
        for (PrototypeSpec.Screen sc : raw.screens()) {
            String id = existingIds.contains(sc.id()) ? sc.id()
                    : byTitle.getOrDefault(sc.title().strip().toLowerCase(Locale.ROOT), sc.id());
            String unique = id;
            for (int n = 2; used.contains(unique); n++) {
                unique = id + "-" + n;
            }
            used.add(unique);
            mapping.put(sc.id(), unique);
        }
        List<PrototypeSpec.Screen> screens = raw.screens().stream()
                .map(sc -> new PrototypeSpec.Screen(mapping.get(sc.id()), sc.title(), sc.description(),
                        sc.requirementKeys(), PrototypeTree.mapActions(sc.components(),
                        a -> a.target() == null ? a : a.withTarget(mapping.getOrDefault(a.target(), a.target())))))
                .toList();
        return new PrototypeSpec(raw.title(), mapping.getOrDefault(raw.startScreen(), raw.startScreen()),
                raw.initialState(), screens, raw.schemaVersion());
    }

    private static PrototypeSpec normalize(PrototypeSpec spec) {
        return new PrototypeSpec(spec.title().strip(), spec.startScreen(),
                spec.initialState() == null ? Map.of() : spec.initialState(), spec.screens(),
                PrototypeSpec.SCHEMA_VERSION);
    }

    private static Set<String> linkedRequirements(PrototypeSpec spec) {
        Set<String> keys = new LinkedHashSet<>();
        spec.screens().forEach(sc -> keys.addAll(sc.requirementKeysOrEmpty()));
        return keys;
    }
}
