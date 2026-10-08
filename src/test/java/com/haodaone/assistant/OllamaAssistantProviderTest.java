package com.haodaone.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaAssistantProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestsNativeOllamaToolCallingAndParsesTheModelToolCall() throws Exception {
        AtomicReference<String> payload = new AtomicReference<>();
        HttpServer server = startServer("""
                {"message":{"role":"assistant","content":"","tool_calls":[{"function":{"name":"get_my_leave_balance","arguments":{}}}]}}
                """, payload);
        try {
            OllamaAssistantProvider provider = provider(server);
            var tool = new AiProvider.ModelTool("get_my_leave_balance", "Get authenticated user's balance.",
                    Map.of("type", "object", "properties", Map.of(), "required", List.of(), "additionalProperties", false));

            AiProvider.ModelTurn turn = provider.chat(List.of(AiProvider.ModelMessage.user("What is my balance?")),
                    List.of(tool));

            assertThat(turn.content()).isEmpty();
            assertThat(turn.toolCalls()).containsExactly(new AiProvider.ToolCall("get_my_leave_balance", Map.of()));
            var request = objectMapper.readTree(payload.get());
            assertThat(request.path("model").asText()).isEqualTo("test-model");
            assertThat(request.path("think").asBoolean()).isFalse();
            assertThat(request.path("tools").get(0).path("function").path("name").asText())
                    .isEqualTo("get_my_leave_balance");
            assertThat(payload.get()).doesNotContain("companyId", "userId", "tenantId");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void serializesToolResultsUsingOllamaToolMessageProtocol() throws Exception {
        AtomicReference<String> payload = new AtomicReference<>();
        HttpServer server = startServer("{\"message\":{\"role\":\"assistant\",\"content\":\"You have 8 days left.\"}}", payload);
        try {
            OllamaAssistantProvider provider = provider(server);

            var turn = provider.chat(List.of(
                    AiProvider.ModelMessage.assistantToolCalls(List.of(
                            new AiProvider.ToolCall("get_my_leave_balance", Map.of()))),
                    AiProvider.ModelMessage.tool("get_my_leave_balance", "{\"remainingDays\":8}")
            ), List.of());

            assertThat(turn.content()).isEqualTo("You have 8 days left.");
            var sentMessages = objectMapper.readTree(payload.get()).path("messages");
            assertThat(sentMessages.get(1).path("tool_calls").get(0).path("function").path("name").asText())
                    .isEqualTo("get_my_leave_balance");
            assertThat(sentMessages.get(2).path("role").asText()).isEqualTo("tool");
            assertThat(sentMessages.get(2).path("tool_name").asText()).isEqualTo("get_my_leave_balance");
            assertThat(sentMessages.get(2).path("content").asText()).contains("remainingDays");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsProviderFailureAndMalformedToolArgumentsAsControlledProviderErrors() throws Exception {
        HttpServer server = startServer("""
                {"message":{"role":"assistant","tool_calls":[{"function":{"name":"get_my_leave_balance","arguments":"not-json"}}]}}
                """, new AtomicReference<>());
        try {
            assertThatThrownBy(() -> provider(server).chat(List.of(AiProvider.ModelMessage.user("hello")), List.of()))
                    .isInstanceOf(AssistantProviderException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void convertsNonSuccessfulOllamaResponsesIntoControlledProviderErrors() throws Exception {
        HttpServer server = startServer("service unavailable", new AtomicReference<>(), 503);
        try {
            assertThatThrownBy(() -> provider(server).chat(List.of(AiProvider.ModelMessage.user("hello")), List.of()))
                    .isInstanceOf(AssistantProviderException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsUnsupportedProviderConfiguration() {
        assertThat(new OllamaAssistantProvider(objectMapper, "openai-compatible",
                "http://127.0.0.1:11434", "model", 0.2, 128,
                Duration.ofSeconds(10), Duration.ofMinutes(3)).isConfigured()).isFalse();
        assertThat(new OllamaAssistantProvider(objectMapper, "ollama",
                "https://127.0.0.1:11434", "model", 0.2, 128,
                Duration.ofSeconds(10), Duration.ofMinutes(3)).isConfigured()).isFalse();
    }

    @Test
    void acceptsAConfigurableRemoteInferenceTimeoutWithinTheFiniteLimit() {
        assertThat(new OllamaAssistantProvider(objectMapper, "ollama",
                "http://127.0.0.1:11434", "model", 0.2, 128,
                Duration.ofSeconds(10), Duration.ofMinutes(3)).isConfigured()).isTrue();
        assertThat(new OllamaAssistantProvider(objectMapper, "ollama",
                "http://127.0.0.1:11434", "model", 0.2, 128,
                Duration.ofSeconds(10), Duration.ofMinutes(11)).isConfigured()).isFalse();
    }

    private OllamaAssistantProvider provider(HttpServer server) {
        return new OllamaAssistantProvider(objectMapper, "ollama",
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-model", 0.2, 128,
                Duration.ofSeconds(5), Duration.ofSeconds(5));
    }

    private HttpServer startServer(String response, AtomicReference<String> payload) throws Exception {
        return startServer(response, payload, 200);
    }

    private HttpServer startServer(String response, AtomicReference<String> payload, int status) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }
}
