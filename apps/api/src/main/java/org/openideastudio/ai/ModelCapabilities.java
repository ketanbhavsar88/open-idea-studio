package org.openideastudio.ai;

/**
 * What a configured model can do. Behaviour is never assumed: native JSON-schema
 * output stays off until it has been tested for a given model (design doc §9.5).
 */
public record ModelCapabilities(boolean supportsSystemPrompt, boolean supportsJsonSchema, int contextTokens) {
}
