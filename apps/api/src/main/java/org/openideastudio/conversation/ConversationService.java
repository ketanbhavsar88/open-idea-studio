package org.openideastudio.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.openideastudio.ai.ModelGateway;
import org.openideastudio.ai.ModelMessage;
import org.openideastudio.artifact.Artifact;
import org.openideastudio.artifact.ArtifactData;
import org.openideastudio.artifact.ArtifactRepository;
import org.openideastudio.artifact.ArtifactService;
import org.openideastudio.artifact.ArtifactType;
import org.openideastudio.common.BadRequestException;
import org.openideastudio.project.Project;
import org.openideastudio.project.ProjectService;
import org.openideastudio.revision.Revision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Conversation orchestrator for discovery. The user's message is committed
 * before the model is called, so it survives model failures; the model call
 * runs outside any transaction; its validated output is applied atomically
 * together with a new revision.
 */
@Service
public class ConversationService {

    static final String OPERATION = "discovery-turn";
    private static final int HISTORY_MESSAGES = 12;

    private final MessageRepository messages;
    private final ArtifactRepository artifactRepository;
    private final ArtifactService artifacts;
    private final ProjectService projects;
    private final ModelGateway model;
    private final TransactionTemplate tx;
    private final JsonMapper json;

    public ConversationService(MessageRepository messages, ArtifactRepository artifactRepository,
                               ArtifactService artifacts, ProjectService projects, ModelGateway model,
                               TransactionTemplate tx, JsonMapper json) {
        this.messages = messages;
        this.artifactRepository = artifactRepository;
        this.artifacts = artifacts;
        this.projects = projects;
        this.model = model;
        this.tx = tx;
        this.json = json;
    }

    public List<ConversationMessage> list(UUID projectId) {
        projects.get(projectId);
        return messages.list(projectId);
    }

    /** Saves the user's message, then asks the model for the next discovery turn. */
    public TurnResult send(UUID projectId, String content) {
        projects.get(projectId);
        ConversationMessage user = tx.execute(s ->
                messages.insert(projectId, ConversationMessage.USER, content.strip(), json.createObjectNode()));
        return respond(projectId, user);
    }

    /** Retries the assistant reply for the latest user message, after a model failure. */
    public TurnResult retry(UUID projectId) {
        projects.get(projectId);
        ConversationMessage last = messages.last(projectId)
                .filter(m -> m.role().equals(ConversationMessage.USER))
                .orElseThrow(() -> new BadRequestException("There is no unanswered message to retry."));
        return respond(projectId, last);
    }

    /** Starts discovery from the project's idea when the conversation is empty. */
    public TurnResult start(UUID projectId) {
        Project project = projects.get(projectId);
        if (!messages.list(projectId).isEmpty()) {
            throw new BadRequestException("Discovery has already started for this project.");
        }
        String opening = project.description().isBlank()
                ? "I'd like to start shaping my idea for \"" + project.name() + "\"."
                : project.description();
        return send(projectId, opening);
    }

    private TurnResult respond(UUID projectId, ConversationMessage userMessage) {
        Project project = projects.get(projectId);
        List<Artifact> current = artifactRepository.list(projectId, null);

        List<ModelMessage> history = new ArrayList<>();
        history.add(ModelMessage.user(DiscoveryPrompt.context(project, current)
                + "\nThe conversation follows. Respond to the latest user message."));
        history.add(ModelMessage.model("{\"reply\":\"Understood.\",\"ideaSummary\":null,\"nextQuestion\":null,"
                + "\"proposals\":[],\"answeredQuestions\":[]}"));
        for (ConversationMessage m : messages.recent(projectId, HISTORY_MESSAGES)) {
            history.add(m.role().equals(ConversationMessage.USER)
                    ? ModelMessage.user(m.content()) : ModelMessage.model(m.content()));
        }

        // Network call: no transaction or lock is held while waiting.
        DiscoveryTurn turn = model.generate(OPERATION, DiscoveryPrompt.INSTRUCTIONS, history, DiscoveryTurn.class);
        return tx.execute(s -> apply(projectId, userMessage, turn));
    }

    private TurnResult apply(UUID projectId, ConversationMessage userMessage, DiscoveryTurn turn) {
        Project project = projects.lock(projectId);
        List<String> changed = new ArrayList<>();

        if (turn.ideaSummary() != null) {
            DiscoveryTurn.IdeaSummary summary = turn.ideaSummary();
            String key = artifacts.upsertSummary(projectId, "Idea summary", new ArtifactData(
                    summary.summary(), null, null, null, null, null, null, null, null,
                    summary.targetUsers(), summary.problem(), null, null, null));
            if (key != null) {
                changed.add(key);
            }
        }

        // Only keys of existing open questions are accepted from the model.
        for (DiscoveryTurn.AnsweredQuestion answered : nullSafe(turn.answeredQuestions())) {
            artifactRepository.find(projectId, answered.key())
                    .filter(a -> a.type() == ArtifactType.QUESTION && a.status().equals("open"))
                    .ifPresent(q -> {
                        ArtifactData d = q.data();
                        artifactRepository.update(projectId, q.key(), q.title(), "answered", new ArtifactData(
                                d.description(), null, null, null, null, null, answered.answer(), d.why(),
                                d.options(), null, null, d.origin(), null, null));
                        changed.add(q.key());
                    });
        }

        List<ArtifactService.NewArtifact> proposals = new ArrayList<>();
        for (DiscoveryTurn.Proposal p : turn.proposals()) {
            proposals.add(new ArtifactService.NewArtifact(ArtifactType.from(p.type()), p.title().strip(),
                    new ArtifactData(p.description(), p.category(), p.priority(), p.acceptanceCriteria(),
                            p.rationale(), p.impact(), null, null, null, null, null, null, null, null)));
        }
        String questionKey = null;
        if (turn.nextQuestion() != null) {
            DiscoveryTurn.NextQuestion q = turn.nextQuestion();
            proposals.add(new ArtifactService.NewArtifact(ArtifactType.QUESTION, q.text().strip(),
                    new ArtifactData(null, null, null, null, null, null, null, q.why(), q.options(), null, null, null, null, null)));
        }
        List<Artifact> created = artifacts.addProposals(projectId, proposals);
        created.forEach(a -> changed.add(a.key()));
        if (turn.nextQuestion() != null) {
            questionKey = created.stream()
                    .filter(a -> a.type() == ArtifactType.QUESTION && a.title().equals(turn.nextQuestion().text().strip()))
                    .map(Artifact::key).findFirst().orElse(null);
        }

        ObjectNode metadata = json.createObjectNode();
        metadata.put("model", model.provider().name());
        metadata.putPOJO("createdArtifacts", created.stream().map(Artifact::key).toList());
        if (turn.nextQuestion() != null) {
            ObjectNode question = metadata.putObject("question");
            question.put("key", questionKey);
            question.put("text", turn.nextQuestion().text());
            question.put("why", turn.nextQuestion().why());
            question.putPOJO("options", nullSafe(turn.nextQuestion().options()));
        }
        ConversationMessage assistant = messages.insert(projectId, ConversationMessage.ASSISTANT, turn.reply(), metadata);

        if (!changed.isEmpty()) {
            boolean summaryChanged = changed.contains(ArtifactType.SUMMARY.prefix());
            projects.commitRevision(project, "Discovery: " + describe(created.size(),
                            changed.size() - created.size() - (summaryChanged ? 1 : 0), summaryChanged),
                    Revision.BY_AI, changed);
        }
        return new TurnResult(userMessage, assistant, created);
    }

    private static String describe(int created, int updated, boolean summaryChanged) {
        List<String> parts = new ArrayList<>();
        if (summaryChanged) {
            parts.add("idea summary updated");
        }
        if (created > 0) {
            parts.add(created + " new item" + (created == 1 ? "" : "s") + " proposed");
        }
        if (updated > 0) {
            parts.add(updated + " question" + (updated == 1 ? "" : "s") + " answered");
        }
        return String.join(", ", parts);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    public record TurnResult(ConversationMessage userMessage, ConversationMessage assistantMessage,
                             List<Artifact> createdArtifacts) {
    }
}
