package org.openideastudio.conversation;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/messages")
public class ConversationController {

    private final ConversationService conversation;

    public ConversationController(ConversationService conversation) {
        this.conversation = conversation;
    }

    @GetMapping
    List<ConversationMessage> list(@PathVariable UUID projectId) {
        return conversation.list(projectId);
    }

    @PostMapping
    ConversationService.TurnResult send(@PathVariable UUID projectId, @Valid @RequestBody SendRequest request) {
        return conversation.send(projectId, request.content());
    }

    /** Begins discovery from the project's idea description. */
    @PostMapping("/start")
    ConversationService.TurnResult start(@PathVariable UUID projectId) {
        return conversation.start(projectId);
    }

    /** Re-asks the model to answer the latest user message after a failure. */
    @PostMapping("/retry")
    ConversationService.TurnResult retry(@PathVariable UUID projectId) {
        return conversation.retry(projectId);
    }

    record SendRequest(@NotBlank @Size(max = 8000) String content) {
    }
}
