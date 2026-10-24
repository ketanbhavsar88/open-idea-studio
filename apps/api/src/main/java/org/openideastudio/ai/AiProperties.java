package org.openideastudio.ai;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai")
public record AiProperties(
        String provider,
        int timeoutSeconds,
        int maxRetries,
        long minIntervalMs,
        Gemini gemini,
        Ollama ollama,
        Map<String, String> operationModels) {

    public record Gemini(String apiKey, String model, boolean supportsSystemPrompt) {
    }

    public record Ollama(String baseUrl, String model) {
    }

    /** Model override for one operation (key like "discovery-turn"), or null for the provider default. */
    public String modelFor(String operation) {
        String model = operationModels == null ? null : operationModels.get(operation);
        return model == null || model.isBlank() ? null : model;
    }
}
