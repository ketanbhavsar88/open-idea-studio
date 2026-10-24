package org.openideastudio.conversation;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** @param metadata user-facing outcome details (created artifact keys, the asked question); never model reasoning */
public record ConversationMessage(UUID id, String role, String content, JsonNode metadata, Instant createdAt) {

    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";
}
