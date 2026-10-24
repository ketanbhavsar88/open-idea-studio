package org.openideastudio.ai;

import java.util.ArrayList;
import java.util.List;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;

/** Calls a Gemma (or Gemini) model through the hosted Gemini API. */
public class GeminiApiProvider implements ModelProvider {

    private final AiProperties.Gemini config;
    private final Client client;

    public GeminiApiProvider(AiProperties properties) {
        this.config = properties.gemini();
        this.client = isConfigured()
                ? Client.builder()
                        .apiKey(config.apiKey())
                        .httpOptions(HttpOptions.builder().timeout(properties.timeoutSeconds() * 1000).build())
                        .build()
                : null;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public String defaultModel() {
        return config.model();
    }

    @Override
    public boolean isConfigured() {
        return config.apiKey() != null && !config.apiKey().isBlank()
                && !config.apiKey().equals("your-gemini-api-key");
    }

    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(config.supportsSystemPrompt(), false, 128_000);
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        if (client == null) {
            throw new ModelUnavailableException(ModelUnavailableException.Reason.NOT_CONFIGURED,
                    "GEMINI_API_KEY is not set. Add it to infra/.env and restart the API.");
        }
        String model = request.model() != null ? request.model() : config.model();

        List<Content> contents = new ArrayList<>();
        GenerateContentConfig.Builder generation = GenerateContentConfig.builder().temperature(0.4f);
        if (capabilities().supportsSystemPrompt()) {
            generation.systemInstruction(Content.fromParts(Part.fromText(request.system())));
        } else {
            // Older Gemma versions reject system instructions: send them as the first user turn.
            contents.add(content("user", request.system()));
            contents.add(content("model", "Understood."));
        }
        for (ModelMessage m : request.messages()) {
            contents.add(content(m.role() == ModelMessage.Role.USER ? "user" : "model", m.text()));
        }

        try {
            GenerateContentResponse response = client.models.generateContent(model, contents, generation.build());
            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new ModelUnavailableException(ModelUnavailableException.Reason.UNAVAILABLE,
                        "The model returned an empty response.");
            }
            return new ModelResponse(text, model);
        } catch (ApiException e) {
            throw map(e);
        } catch (ModelUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            if (isTimeout(e)) {
                throw new ModelUnavailableException(ModelUnavailableException.Reason.TIMEOUT,
                        "The model did not respond in time.", e);
            }
            throw new ModelUnavailableException(ModelUnavailableException.Reason.UNAVAILABLE,
                    "Could not reach the Gemini API.", e);
        }
    }

    private static ModelUnavailableException map(ApiException e) {
        return switch (e.code()) {
            case 429 -> new ModelUnavailableException(ModelUnavailableException.Reason.RATE_LIMITED,
                    "The Gemini API rate limit or quota was reached. Wait a minute and try again.", e);
            case 401, 403 -> new ModelUnavailableException(ModelUnavailableException.Reason.NOT_CONFIGURED,
                    "The Gemini API rejected the API key. Check GEMINI_API_KEY.", e);
            case 404 -> new ModelUnavailableException(ModelUnavailableException.Reason.NOT_CONFIGURED,
                    "The configured model was not found. Check GEMINI_MODEL.", e);
            case 408, 504 -> new ModelUnavailableException(ModelUnavailableException.Reason.TIMEOUT,
                    "The model did not respond in time.", e);
            default -> new ModelUnavailableException(ModelUnavailableException.Reason.UNAVAILABLE,
                    "The Gemini API returned an error (" + e.code() + ").", e);
        };
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.http.HttpTimeoutException
                    || t instanceof java.io.InterruptedIOException) {
                return true;
            }
        }
        return false;
    }

    private static Content content(String role, String text) {
        return Content.builder().role(role).parts(List.of(Part.fromText(text))).build();
    }
}
