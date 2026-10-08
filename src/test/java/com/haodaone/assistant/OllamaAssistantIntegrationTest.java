package com.haodaone.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_OLLAMA_INTEGRATION", matches = "(?i)true")
class OllamaAssistantIntegrationTest {
    @Test
    void configuredOllamaModelCanReturnNativeToolCall() {
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://127.0.0.1:11434");
        String model = System.getenv().getOrDefault("OLLAMA_MODEL", "gpt-oss:20b");
        var provider = new OllamaAssistantProvider(
                new ObjectMapper(), "ollama", baseUrl, model, 0, 128,
                Duration.ofSeconds(10), Duration.ofMinutes(3));
        var tool = new AiProvider.ModelTool("integration_probe",
                "Call this when asked to retrieve an integration probe value.",
                Map.of("type", "object", "properties", Map.of(), "required", List.of(),
                        "additionalProperties", false));

        var response = provider.chat(List.of(
                AiProvider.ModelMessage.user("Use the integration_probe tool now. Do not answer without calling it.")
        ), List.of(tool));

        assertThat(response.toolCalls()).anySatisfy(call -> assertThat(call.name()).isEqualTo("integration_probe"));
    }
}
