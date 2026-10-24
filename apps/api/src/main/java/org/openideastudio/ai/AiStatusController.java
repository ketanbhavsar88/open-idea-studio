package org.openideastudio.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets the UI show which provider is active and whether it is configured. Never returns secrets. */
@RestController
public class AiStatusController {

    private final ModelProvider provider;

    public AiStatusController(ModelProvider provider) {
        this.provider = provider;
    }

    @GetMapping("/api/v1/ai/status")
    Status status() {
        return new Status(provider.name(), provider.defaultModel(), provider.isConfigured());
    }

    record Status(String provider, String model, boolean configured) {
    }
}
