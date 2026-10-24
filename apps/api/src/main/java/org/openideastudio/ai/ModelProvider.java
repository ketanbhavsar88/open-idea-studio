package org.openideastudio.ai;

/** The only way business logic talks to a model. Implementations must not leak provider types. */
public interface ModelProvider {

    String name();

    /** The default model name, for status display. */
    String defaultModel();

    /** False when required configuration (e.g. an API key) is missing. */
    boolean isConfigured();

    ModelCapabilities capabilities();

    /** @throws ModelUnavailableException for rate limits, timeouts, auth and server failures */
    ModelResponse generate(ModelRequest request);
}
