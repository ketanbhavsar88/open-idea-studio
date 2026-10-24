package org.openideastudio.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs one structured AI operation: throttles calls, asks for JSON that matches
 * a schema described in the prompt, then parses and validates the reply,
 * retrying with the validation errors when it is unusable. Model output is
 * treated as untrusted input throughout.
 */
@Component
public class ModelGateway {

    private static final Logger log = LoggerFactory.getLogger(ModelGateway.class);

    private final ModelProvider provider;
    private final AiProperties properties;
    private final Validator validator;
    private final JsonMapper strictJson;
    private long lastCallAt;

    public ModelGateway(ModelProvider provider, AiProperties properties, Validator validator, JsonMapper json) {
        this.provider = provider;
        this.properties = properties;
        this.validator = validator;
        this.strictJson = json.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    public ModelProvider provider() {
        return provider;
    }

    /**
     * @param operation    kebab-case operation name, used for per-operation model overrides and logs
     * @param instructions task instructions, including the JSON schema of {@code type}
     */
    public <T> T generate(String operation, String instructions, List<ModelMessage> messages, Class<T> type) {
        return generate(operation, instructions, messages, type, value -> List.of());
    }

    /**
     * Like {@link #generate(String, String, List, Class)}, plus a semantic check
     * (e.g. "references existing nodes") whose errors also trigger a retry.
     */
    public <T> T generate(String operation, String instructions, List<ModelMessage> messages, Class<T> type,
                          Function<T, List<String>> semanticCheck) {
        String system = instructions + "\n\nRespond with a single JSON object only. No markdown, no code fences, no text outside the JSON.";
        List<ModelMessage> conversation = new ArrayList<>(messages);
        List<String> errors = List.of();

        for (int attempt = 0; attempt <= properties.maxRetries(); attempt++) {
            throttle();
            ModelResponse response = provider.generate(new ModelRequest(operation, system, conversation,
                    properties.modelFor(operation), true, Duration.ofSeconds(properties.timeoutSeconds())));
            errors = new ArrayList<>();
            T parsed = parse(response.text(), type, errors);
            if (parsed != null) {
                errors.addAll(semanticCheck.apply(parsed));
                if (errors.isEmpty()) {
                    return parsed;
                }
            }
            // Log only the error shape, never the project content in the response.
            log.warn("AI operation {} returned invalid output (attempt {}): {}", operation, attempt + 1, errors);
            conversation.add(ModelMessage.model(response.text()));
            conversation.add(ModelMessage.user("Your previous reply was invalid: " + String.join("; ", errors)
                    + ". Reply again with only the corrected JSON object."));
        }
        throw new InvalidModelOutputException(errors);
    }

    private <T> T parse(String text, Class<T> type, List<String> errors) {
        String json = extractJson(text);
        if (json == null) {
            errors.add("no JSON object found");
            return null;
        }
        T value;
        try {
            value = strictJson.readValue(json, type);
        } catch (JacksonException e) {
            errors.add("JSON does not match the schema: " + e.getOriginalMessage());
            return null;
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        for (ConstraintViolation<T> v : violations) {
            errors.add(v.getPropertyPath() + " " + v.getMessage());
        }
        return violations.isEmpty() ? value : null;
    }

    /** Strips code fences and any prose around the outermost JSON object. */
    static String extractJson(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : null;
    }

    private synchronized void throttle() {
        long wait = lastCallAt + properties.minIntervalMs() - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ModelUnavailableException(ModelUnavailableException.Reason.UNAVAILABLE, "Interrupted.");
            }
        }
        lastCallAt = System.currentTimeMillis();
    }
}
