package org.openideastudio.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.openideastudio.ai.ModelGateway;
import org.openideastudio.ai.ModelMessage;
import org.openideastudio.artifact.Artifact;
import org.openideastudio.artifact.ArtifactData;
import org.openideastudio.artifact.ArtifactKeys;
import org.openideastudio.artifact.ArtifactRepository;
import org.openideastudio.artifact.ArtifactType;
import org.openideastudio.artifact.RelationshipRepository;
import org.openideastudio.changeset.ChangeSet;
import org.openideastudio.changeset.ChangeSetRepository;
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
 * Flows: direct edits, AI generation, and AI change proposals. All three end in
 * the same validated {@link FlowGraph}, the same artifact row, and a revision.
 * Model calls never run inside a transaction.
 */
@Service
public class FlowService {

    static final String IMPLEMENTS = "implements";

    private final ArtifactRepository artifacts;
    private final RelationshipRepository relationships;
    private final ChangeSetRepository changeSets;
    private final ProjectService projects;
    private final ModelGateway model;
    private final TransactionTemplate tx;

    public FlowService(ArtifactRepository artifacts, RelationshipRepository relationships,
                       ChangeSetRepository changeSets, ProjectService projects, ModelGateway model,
                       TransactionTemplate tx) {
        this.artifacts = artifacts;
        this.relationships = relationships;
        this.changeSets = changeSets;
        this.projects = projects;
        this.model = model;
        this.tx = tx;
    }

    public List<Artifact> list(UUID projectId) {
        projects.get(projectId);
        return artifacts.list(projectId, ArtifactType.FLOW);
    }

    public Artifact get(UUID projectId, String key) {
        return artifacts.find(projectId, key)
                .filter(a -> a.type() == ArtifactType.FLOW)
                .orElseThrow(() -> new NotFoundException("Flow not found."));
    }

    /** Creates an empty flow with start and end nodes, for drawing by hand. */
    public Artifact create(UUID projectId, String title) {
        FlowGraph graph = new FlowGraph(List.of(
                new FlowNode("node-start", "start", "Start", null, new FlowNode.Position(0, 0), List.of()),
                new FlowNode("node-end", "end", "End", null, new FlowNode.Position(0, 280), List.of())),
                List.of(), FlowGraph.SCHEMA_VERSION);
        return tx.execute(s -> insertFlow(projectId, title, null, graph, Revision.BY_USER, "Added flow "));
    }

    /** Saves a graph edited on the canvas. */
    public SaveResult save(UUID projectId, String key, String title, FlowGraph graph, long expectedVersion) {
        return tx.execute(s -> {
            Project project = projects.lock(projectId);
            Artifact current = get(projectId, key);
            if (current.version() != expectedVersion) {
                throw new ConflictException("This flow was changed elsewhere. Reload it and apply your edit again.");
            }
            FlowValidator.Result check = requireValid(projectId, graph);
            String newTitle = title == null || title.isBlank() ? current.title() : title.strip();
            Artifact saved = writeGraph(projectId, current, newTitle, graph);
            projects.commitRevision(project, "Edited flow " + key, Revision.BY_USER, List.of(key));
            return new SaveResult(saved, check.warnings());
        });
    }

    /** Asks the model for a new flow built from confirmed requirements. */
    public SaveResult generate(UUID projectId, String titleHint, List<String> requestedKeys) {
        Project project = projects.get(projectId);
        List<Artifact> all = artifacts.list(projectId, null);
        List<Artifact> requirements = all.stream()
                .filter(a -> a.type() == ArtifactType.REQUIREMENT && a.status().equals("confirmed"))
                .filter(a -> requestedKeys == null || requestedKeys.isEmpty() || requestedKeys.contains(a.key()))
                .toList();
        if (requirements.isEmpty()) {
            throw new BadRequestException("Confirm at least one requirement before generating a flow.");
        }
        List<Artifact> decisions = all.stream()
                .filter(a -> a.type() == ArtifactType.DECISION && a.status().equals("confirmed")).toList();
        Set<String> allowedKeys = new HashSet<>(requirements.stream().map(Artifact::key).toList());

        String context = "Project: " + project.name() + "\n"
                + (titleHint == null || titleHint.isBlank() ? "" : "Flow to design: " + titleHint + "\n")
                + FlowPrompts.requirements(requirements) + FlowPrompts.decisions(decisions);
        GeneratedFlow generated = model.generate("generate-flow", FlowPrompts.GENERATE,
                List.of(ModelMessage.user(context)), GeneratedFlow.class,
                g -> checkGenerated(g, allowedKeys));

        FlowGraph graph = toGraph(generated);
        String title = titleHint == null || titleHint.isBlank() ? generated.title() : titleHint.strip();
        return tx.execute(s -> {
            FlowValidator.Result check = requireValid(projectId, graph);
            Artifact created = insertFlow(projectId, title, generated.description(), graph, Revision.BY_AI,
                    "Generated flow ");
            return new SaveResult(created, check.warnings());
        });
    }

    /** Asks the model for a minimal set of operations and stores them as a pending change set. */
    public Proposal propose(UUID projectId, String key, String instruction) {
        Artifact flow = get(projectId, key);
        Project project = projects.get(projectId);
        FlowGraph graph = graphOf(flow);
        List<Artifact> requirements = artifacts.list(projectId, ArtifactType.REQUIREMENT).stream()
                .filter(a -> !a.status().equals("rejected")).toList();
        Set<String> requirementKeys = new HashSet<>(requirements.stream().map(Artifact::key).toList());

        String context = "Flow: " + flow.title() + "\n" + FlowPrompts.graph(graph)
                + FlowPrompts.requirements(requirements) + "\nInstruction: " + instruction;
        FlowChangeProposal proposal = model.generate("propose-flow-change", FlowPrompts.PROPOSE_CHANGE,
                List.of(ModelMessage.user(context)), FlowChangeProposal.class,
                p -> checkOperations(graph, sanitize(p.operations()), requirementKeys));

        FlowOperations.Result preview = FlowOperations.apply(graph, sanitize(proposal.operations()));
        List<String> warnings = new ArrayList<>(proposal.warnings() == null ? List.of() : proposal.warnings());
        warnings.addAll(FlowValidator.validate(preview.graph(), requirementKeys).warnings());
        if (proposal.operations().isEmpty() && warnings.isEmpty()) {
            warnings.add("The AI did not propose any changes.");
        }
        ChangeSet saved = tx.execute(s -> changeSets.insert(projectId, ChangeSet.FLOW_EDIT,
                project.currentRevisionId(), key, flow.version(), instruction, proposal.summary(),
                preview.resolved(), warnings));
        return new Proposal(saved, preview.graph());
    }

    public Proposal getProposal(UUID projectId, UUID changeSetId) {
        ChangeSet cs = changeSets.find(projectId, changeSetId)
                .orElseThrow(() -> new NotFoundException("Change set not found."));
        Artifact flow = get(projectId, cs.targetArtifactKey());
        return new Proposal(cs, FlowOperations.apply(graphOf(flow), cs.operations()).graph());
    }

    public List<ChangeSet> pending(UUID projectId, String key) {
        return changeSets.listPending(projectId, key);
    }

    /** Applies a pending change set if its flow has not changed since it was proposed. */
    public ApplyResult apply(UUID projectId, UUID changeSetId) {
        // Staleness is recorded in its own transaction so it survives the conflict response.
        ChangeSet stale = tx.execute(s -> {
            projects.lock(projectId);
            ChangeSet cs = pendingChangeSet(projectId, changeSetId);
            Artifact flow = get(projectId, cs.targetArtifactKey());
            return flow.version() != cs.targetArtifactVersion() ? changeSets.markState(cs.id(), "stale", null) : null;
        });
        if (stale != null) {
            throw new ConflictException("The flow changed after this proposal was made. Ask the AI again.");
        }
        return tx.execute(s -> {
            Project project = projects.lock(projectId);
            ChangeSet cs = pendingChangeSet(projectId, changeSetId);
            Artifact flow = get(projectId, cs.targetArtifactKey());
            FlowOperations.Result result = FlowOperations.apply(graphOf(flow), cs.operations());
            if (!result.errors().isEmpty()) {
                throw new ValidationFailedException("invalid-change-set", "The proposal no longer applies.", result.errors());
            }
            FlowValidator.Result check = requireValid(projectId, result.graph());
            Artifact saved = writeGraph(projectId, flow, flow.title(), result.graph());
            Project updated = projects.commitRevision(project, "Applied AI change to " + flow.key() + ": " + cs.summary(),
                    Revision.BY_AI, List.of(flow.key()));
            ChangeSet applied = changeSets.markState(cs.id(), "applied", updated.currentRevisionId());
            return new ApplyResult(applied, saved, check.warnings());
        });
    }

    public ChangeSet reject(UUID projectId, UUID changeSetId) {
        return tx.execute(s -> {
            projects.lock(projectId);
            pendingChangeSet(projectId, changeSetId);
            return changeSets.markState(changeSetId, "rejected", null);
        });
    }

    public record SaveResult(Artifact flow, List<String> warnings) {
    }

    public record Proposal(ChangeSet changeSet, FlowGraph preview) {
    }

    public record ApplyResult(ChangeSet changeSet, Artifact flow, List<String> warnings) {
    }

    // --- internals ---------------------------------------------------------------------------

    private Artifact insertFlow(UUID projectId, String title, String description, FlowGraph graph,
                                String createdBy, String summaryPrefix) {
        Project project = projects.lock(projectId);
        String key = ArtifactKeys.generate(ArtifactType.FLOW, title, artifacts.keys(projectId));
        Artifact created = artifacts.insert(projectId, key, ArtifactType.FLOW, title.strip(), "draft",
                flowData(description, graph, createdBy.equals(Revision.BY_USER) ? "user" : "ai"));
        relationships.replace(projectId, key, IMPLEMENTS, linkedRequirements(graph));
        projects.commitRevision(project, summaryPrefix + key, createdBy, List.of(key));
        return created;
    }

    private Artifact writeGraph(UUID projectId, Artifact current, String title, FlowGraph graph) {
        ArtifactData d = current.data();
        Artifact saved = artifacts.update(projectId, current.key(), title, current.status(),
                flowData(d.description(), graph, d.origin()));
        relationships.replace(projectId, current.key(), IMPLEMENTS, linkedRequirements(graph));
        return saved;
    }

    private FlowValidator.Result requireValid(UUID projectId, FlowGraph graph) {
        Set<String> requirementKeys = new HashSet<>(artifacts.list(projectId, ArtifactType.REQUIREMENT).stream()
                .map(Artifact::key).toList());
        FlowValidator.Result check = FlowValidator.validate(graph, requirementKeys);
        if (!check.valid()) {
            throw new ValidationFailedException("invalid-graph", "The flow is not valid.", check.errors());
        }
        return check;
    }

    private ChangeSet pendingChangeSet(UUID projectId, UUID id) {
        ChangeSet cs = changeSets.find(projectId, id).orElseThrow(() -> new NotFoundException("Change set not found."));
        if (!cs.state().equals("pending")) {
            throw new ConflictException("This proposal is already " + cs.state() + ".");
        }
        return cs;
    }

    static FlowGraph graphOf(Artifact flow) {
        return flow.data().graph() == null ? FlowGraph.empty() : flow.data().graph();
    }

    private static ArtifactData flowData(String description, FlowGraph graph, String origin) {
        return new ArtifactData(description, null, null, null, null, null, null, null, null, null, null, origin, graph);
    }

    private static Set<String> linkedRequirements(FlowGraph graph) {
        Set<String> keys = new LinkedHashSet<>();
        graph.nodes().forEach(n -> keys.addAll(n.requirementKeysOrEmpty()));
        return keys;
    }

    /** The model may not choose IDs or positions for new items; the backend assigns them. */
    static List<FlowOperation> sanitize(List<FlowOperation> ops) {
        return ops.stream().map(o -> switch (o.op()) {
            case "addNode" -> new FlowOperation(o.op(), null, o.ref(), o.nodeType(), o.label(), o.description(),
                    o.requirementKeys(), null, null, null, null, null);
            case "addEdge" -> new FlowOperation(o.op(), null, null, null, null, null, null, o.from(), o.to(), null,
                    o.edgeLabel(), null);
            default -> new FlowOperation(o.op(), o.nodeId(), null, o.nodeType(), o.label(), o.description(),
                    o.requirementKeys(), null, null, o.edgeId(), null, null);
        }).toList();
    }

    private static List<String> checkOperations(FlowGraph graph, List<FlowOperation> ops, Set<String> requirementKeys) {
        FlowOperations.Result result = FlowOperations.apply(graph, ops);
        List<String> errors = new ArrayList<>(result.errors());
        if (errors.isEmpty()) {
            errors.addAll(FlowValidator.validate(result.graph(), requirementKeys).errors());
        }
        return errors;
    }

    private static List<String> checkGenerated(GeneratedFlow g, Set<String> allowedKeys) {
        List<String> errors = new ArrayList<>();
        Set<String> refs = new HashSet<>();
        for (GeneratedFlow.Node n : g.nodes()) {
            if (!refs.add(n.ref())) {
                errors.add("duplicate node ref '" + n.ref() + "'");
            }
            for (String k : n.requirementKeys() == null ? List.<String>of() : n.requirementKeys()) {
                if (!allowedKeys.contains(k)) {
                    errors.add("node '" + n.ref() + "' uses unknown requirement key '" + k + "'; use only listed keys");
                }
            }
        }
        for (GeneratedFlow.Edge e : g.edges()) {
            if (!refs.contains(e.from()) || !refs.contains(e.to())) {
                errors.add("edge " + e.from() + "->" + e.to() + " uses an unknown ref");
            }
        }
        if (g.nodes().stream().noneMatch(n -> n.type().equals("start"))) {
            errors.add("there must be a start node");
        }
        if (g.nodes().stream().noneMatch(n -> n.type().equals("end"))) {
            errors.add("there must be at least one end node");
        }
        return errors;
    }

    private static FlowGraph toGraph(GeneratedFlow g) {
        Map<String, String> ids = new HashMap<>();
        Set<String> takenNodes = new HashSet<>();
        List<FlowNode> nodes = new ArrayList<>();
        for (GeneratedFlow.Node n : g.nodes()) {
            String id = FlowIds.node(n.label(), takenNodes);
            takenNodes.add(id);
            ids.put(n.ref(), id);
            nodes.add(new FlowNode(id, n.type(), n.label().strip(), n.description(), new FlowNode.Position(0, 0),
                    n.requirementKeys() == null ? List.of() : n.requirementKeys()));
        }
        Set<String> takenEdges = new HashSet<>();
        List<FlowEdge> edges = new ArrayList<>();
        for (GeneratedFlow.Edge e : g.edges()) {
            String id = FlowIds.edge(ids.get(e.from()), ids.get(e.to()), takenEdges);
            takenEdges.add(id);
            edges.add(new FlowEdge(id, ids.get(e.from()), ids.get(e.to()), e.label()));
        }
        return new FlowGraph(FlowLayout.layout(nodes, edges), edges, FlowGraph.SCHEMA_VERSION);
    }
}
