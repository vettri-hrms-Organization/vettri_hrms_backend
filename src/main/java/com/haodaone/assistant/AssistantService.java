package com.haodaone.assistant;

import com.haodaone.assistant.dto.AssistantChatRequest;
import com.haodaone.assistant.dto.AssistantChatResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AssistantService {
    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final int HISTORY_WINDOW = 12;
    private static final int MAX_TOOL_RESULT_CHARACTERS = 8_000;
    private static final String PROVIDER_UNAVAILABLE = "Vettri Bot is temporarily unavailable. Please try again.";
    private static final String TOOL_UNAVAILABLE = "I couldn't retrieve that information right now.";
    private static final String TOOL_FORBIDDEN = "You don't have access to that information.";
    private static final String LOOP_LIMIT = "I couldn't complete that request. Please try again.";
    private static final Pattern DEVICE_MAPPING_GUIDANCE_REQUEST = Pattern.compile(
            "\\b(?:map(?:ping)?|assign(?:ment)?)\\b(?=.*\\bdevices?\\b)|\\bdevices?\\b(?=.*\\b(?:map(?:ping)?|assign(?:ment)?)\\b)");

    private final AiProvider aiProvider;
    private final AssistantContextResolver contextResolver;
    private final AssistantConversationStore conversationStore;
    private final AssistantToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final int maxToolIterations;

    public AssistantService(
            AiProvider aiProvider,
            AssistantContextResolver contextResolver,
            AssistantConversationStore conversationStore,
            AssistantToolRegistry toolRegistry,
            ObjectMapper objectMapper,
            @Value("${app.assistant.ai.max-tool-iterations:5}") int maxToolIterations
    ) {
        this.aiProvider = aiProvider;
        this.contextResolver = contextResolver;
        this.conversationStore = conversationStore;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.maxToolIterations = Math.max(1, Math.min(maxToolIterations, 10));
    }

    public AssistantChatResponse chat(AssistantChatRequest request) {
        AssistantContext context = contextResolver.resolve();
        UUID conversationId = conversationId(request.conversationId())
                .orElseGet(() -> conversationStore.create(context.companyId(), context.userId()));
        if (!conversationStore.append(conversationId, context.companyId(), context.userId(), "USER", request.message().trim())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        if (isDeviceMappingGuidanceRequest(request.message())) {
            AssistantToolRegistry.DeviceMappingGuidance guidance = toolRegistry.deviceMappingGuidance(context);
            List<AssistantChatResponse.AssistantAction> actions = guidance.action() == null
                    ? List.of() : List.of(guidance.action());
            return saveResponse(conversationId, context, guidance.message(), "CHAT", actions, false);
        }

        AssistantConversationStore.ConversationSnapshot snapshot = conversationStore
                .get(conversationId, context.companyId(), context.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<AiProvider.ModelMessage> messages = snapshot.messages().stream()
                .skip(Math.max(0, snapshot.messages().size() - HISTORY_WINDOW))
                .map(message -> "USER".equals(message.role())
                        ? AiProvider.ModelMessage.user(message.content())
                        : AiProvider.ModelMessage.assistant(message.content()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        List<AiProvider.ModelTool> availableTools = toolRegistry.availableTools(context);
        if (!aiProvider.isConfigured()) {
            return saveResponse(conversationId, context, PROVIDER_UNAVAILABLE, "ERROR", List.of(), false);
        }

        List<AssistantChatResponse.AssistantAction> actions = new ArrayList<>();
        try {
            for (int iteration = 0; iteration < maxToolIterations; iteration++) {
                AiProvider.ModelTurn modelTurn = aiProvider.chat(messages, availableTools);
                if (modelTurn.toolCalls().isEmpty()) {
                    String answer = modelTurn.content().strip();
                    if (answer.isBlank()) {
                        return saveResponse(conversationId, context, PROVIDER_UNAVAILABLE, "ERROR", List.of(), false);
                    }
                    return saveResponse(conversationId, context, answer, "CHAT", actions, true);
                }

                messages.add(AiProvider.ModelMessage.assistantToolCalls(modelTurn.toolCalls()));
                for (AiProvider.ToolCall toolCall : modelTurn.toolCalls()) {
                    if (availableTools.stream().noneMatch(tool -> tool.name().equals(toolCall.name()))) {
                        log.warn("Rejected unregistered Vettri assistant tool call");
                        return saveResponse(conversationId, context, TOOL_UNAVAILABLE, "ERROR", List.of(), false);
                    }
                    try {
                        AssistantToolRegistry.ToolResult result = toolRegistry.execute(
                                toolCall.name(), toolCall.arguments(), contextResolver.resolve());
                        messages.add(AiProvider.ModelMessage.tool(
                                toolCall.name(), boundedToolResult(result.sanitizedJson())));
                        if (result.action() != null) actions.add(result.action());
                    } catch (AssistantToolException ex) {
                        if (ex.isForbidden()) {
                            return saveResponse(conversationId, context, TOOL_FORBIDDEN, "ERROR", List.of(), false);
                        }
                        return saveResponse(conversationId, context, TOOL_UNAVAILABLE, "ERROR", List.of(), false);
                    } catch (RuntimeException ex) {
                        log.warn("Vettri assistant tool execution failed ({})", toolCall.name());
                        return saveResponse(conversationId, context, TOOL_UNAVAILABLE, "ERROR", List.of(), false);
                    }
                }
            }
            return saveResponse(conversationId, context, LOOP_LIMIT, "ERROR", List.of(), false);
        } catch (AssistantProviderException ex) {
            return saveResponse(conversationId, context, PROVIDER_UNAVAILABLE, "ERROR", List.of(), false);
        }
    }

    private boolean isDeviceMappingGuidanceRequest(String message) {
        return DEVICE_MAPPING_GUIDANCE_REQUEST.matcher(message.toLowerCase(Locale.ROOT)).find();
    }

    public List<AssistantConversationStore.ConversationSummary> conversations() {
        AssistantContext context = contextResolver.resolve();
        return conversationStore.list(context.companyId(), context.userId());
    }

    public AssistantConversationStore.ConversationSnapshot conversation(UUID conversationId) {
        AssistantContext context = contextResolver.resolve();
        return conversationStore.get(conversationId, context.companyId(), context.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public void archive(UUID conversationId) {
        AssistantContext context = contextResolver.resolve();
        if (!conversationStore.archive(conversationId, context.companyId(), context.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private java.util.Optional<UUID> conversationId(String value) {
        if (value == null || value.isBlank()) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private String boundedToolResult(String result) {
        if (result.length() <= MAX_TOOL_RESULT_CHARACTERS) return result;
        return "{\"truncated\":true,\"message\":\"The result was too large to include in full.\"}";
    }

    private AssistantChatResponse saveResponse(
            UUID conversationId,
            AssistantContext context,
            String message,
            String mode,
            List<AssistantChatResponse.AssistantAction> actions,
            boolean aiEnhanced
    ) {
        String bounded = message.length() > 4000 ? message.substring(0, 4000) : message;
        conversationStore.append(conversationId, context.companyId(), context.userId(), "ASSISTANT", bounded);
        return new AssistantChatResponse(
                bounded,
                "CHAT",
                mode,
                List.copyOf(actions),
                List.of(),
                false,
                aiEnhanced,
                conversationId.toString()
        );
    }
}
