package com.haodaone.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleAssistantProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void onlyEnablesConfiguredHttpsProvider() {
        var valid = new OpenAiCompatibleAssistantProvider(
                objectMapper,
                "openai-compatible",
                "test-key",
                "test-model",
                "https://provider.example/v1/chat/completions"
        );
        var insecureEndpoint = new OpenAiCompatibleAssistantProvider(
                objectMapper,
                "openai-compatible",
                "test-key",
                "test-model",
                "http://provider.example/v1/chat/completions"
        );
        var missingKey = new OpenAiCompatibleAssistantProvider(
                objectMapper,
                "openai-compatible",
                "",
                "test-model",
                "https://provider.example/v1/chat/completions"
        );

        assertThat(valid.isConfigured()).isTrue();
        assertThat(insecureEndpoint.isConfigured()).isFalse();
        assertThat(missingKey.isConfigured()).isFalse();
    }
}
