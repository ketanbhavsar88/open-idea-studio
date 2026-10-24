package org.openideastudio.ai;

import java.time.Duration;
import java.util.List;

/**
 * @param operation kebab-case AI operation name (e.g. "discovery-turn"), for overrides and logs
 * @param model   model override, or null for the provider default
 * @param jsonOnly ask the provider for JSON output where it supports a JSON mode
 */
public record ModelRequest(String operation, String system, List<ModelMessage> messages, String model, boolean jsonOnly,
                           Duration timeout) {
}
