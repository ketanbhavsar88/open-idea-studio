package org.openideastudio.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean
    ModelProvider modelProvider(AiProperties properties) {
        return switch (properties.provider()) {
            case "gemini" -> new GeminiApiProvider(properties);
            case "ollama" -> new OllamaProvider(properties);
            case "fake" -> new FakeProvider();
            default -> throw new IllegalStateException(
                    "Unknown AI_PROVIDER '" + properties.provider() + "'. Use gemini, ollama, or fake.");
        };
    }
}
