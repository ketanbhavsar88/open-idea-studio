package org.openideastudio.ai;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Local, offline model via Ollama's /api/chat endpoint. */
public class OllamaProvider implements ModelProvider {

    private final AiProperties.Ollama config;
    private final RestClient http;

    public OllamaProvider(AiProperties properties) {
        this.config = properties.ollama();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        factory.setReadTimeout(java.time.Duration.ofSeconds(properties.timeoutSeconds()));
        this.http = RestClient.builder().baseUrl(config.baseUrl()).requestFactory(factory).build();
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public String defaultModel() {
        return config.model();
    }

    @Override
    public boolean isConfigured() {
        return config.baseUrl() != null && !config.baseUrl().isBlank();
    }

    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(true, false, 8_192);
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        String model = request.model() != null ? request.model() : config.model();
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.system()));
        for (ModelMessage m : request.messages()) {
            messages.add(Map.of("role", m.role() == ModelMessage.Role.USER ? "user" : "assistant", "content", m.text()));
        }
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "model", model, "messages", messages, "stream", false, "options", Map.of("temperature", 0.4)));
        if (request.jsonOnly()) {
            body.put("format", "json");
        }
        try {
            ChatResponse response = http.post().uri("/api/chat").body(body).retrieve().body(ChatResponse.class);
            if (response == null || response.message() == null || response.message().content() == null) {
                throw new ModelUnavailableException(ModelUnavailableException.Reason.UNAVAILABLE,
                        "Ollama returned an empty response.");
            }
            return new ModelResponse(response.message().content(), model);
        } catch (RestClientResponseException e) {
            throw new ModelUnavailableException(e.getStatusCode().value() == 404
                    ? ModelUnavailableException.Reason.NOT_CONFIGURED
                    : ModelUnavailableException.Reason.UNAVAILABLE,
                    e.getStatusCode().value() == 404
                            ? "Ollama does not have model '" + model + "'. Run: ollama pull " + model
                            : "Ollama returned an error (" + e.getStatusCode().value() + ").", e);
        } catch (ResourceAccessException e) {
            boolean timeout = e.getCause() instanceof java.net.http.HttpTimeoutException
                    || e.getCause() instanceof java.net.SocketTimeoutException;
            throw new ModelUnavailableException(
                    timeout ? ModelUnavailableException.Reason.TIMEOUT : ModelUnavailableException.Reason.UNAVAILABLE,
                    timeout ? "The model did not respond in time." : "Cannot reach Ollama at " + config.baseUrl() + ".", e);
        }
    }

    record ChatResponse(Message message) {
        record Message(String role, String content) {
        }
    }
}
