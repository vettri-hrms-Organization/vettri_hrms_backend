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
    private static final Pattern LEAVE_ACTION_REQUEST = Pattern.compile(
            "(?is)(?=.*\\b(?:leave|time off|day off|cl|el|sl)\\b)(?=.*\\b(?:apply|request|book|take|put in|submit|need|want)\\b).*");
    private static final Pattern LEAVE_GUIDANCE_QUESTION = Pattern.compile(
            "(?is)^\\s*(?:how|where|why|what|should|can i|could i|do i)\\b.*\\b(?:leave|time off|day off)\\b.*");
    private static final Pattern LEAVE_CONFIRMATION = Pattern.compile(
            "(?i)^\\s*(?:yes|yes please|yes,? submit(?: it)?|submit(?: it)?|go ahead|do it|confirm|please submit)\\s*[.!]*\\s*$");
    private static final Pattern LEAVE_CANCELLATION = Pattern.compile(
            "(?i)^\\s*(?:no|cancel|cancel that|don't submit|do not submit|never mind|nevermind)\\s*[.!]*\\s*$");
    private static final Pattern LEAVE_MODIFICATION = Pattern.compile(
            "(?is)^\\s*(?:actually|change|update|modify|make it|move it|instead)\\b.*");

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
        AssistantConversationStore.PendingLeaveAction priorPending = conversationStore
                .pendingLeave(conversationId, context.companyId(), context.userId())
                .orElse(null);
        if (!conversationStore.append(conversationId, context.companyId(), context.userId(), "USER", request.message().trim())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        if (LEAVE_CANCELLATION.matcher(request.message()).matches() && priorPending != null) {
            toolRegistry.cancelPendingLeave(conversationId, context);
            return saveResponse(conversationId, context, "No problem. I haven't submitted anything.",
                    "CHAT", List.of(), false);
        }
        if (LEAVE_CONFIRMATION.matcher(request.message()).matches()) {
            if (priorPending == null) {
                return saveResponse(conversationId, context,
                        "There isn't a leave request waiting for confirmation. I haven't submitted anything.",
                        "CHAT", List.of(), false);
            }
            if (!priorPending.ready()) {
                String missing = priorPending.leaveTypeId() == null
                        ? "Which leave type would you like to use?"
                        : "Which date or date range would you like to take leave?";
                return saveResponse(conversationId, context,
                        "I still need one detail before I can prepare your request. " + missing,
                        "CHAT", List.of(), false);
            }
            try {
                var submitted = toolRegistry.submitConfirmedLeave(conversationId, contextResolver.resolve());
                return saveResponse(conversationId, context,
                        "Done. Your " + submitted.getLeaveTypeName() + " request for "
                                + formatLeavePeriod(submitted.getStartDate(), submitted.getEndDate())
                                + " has been submitted.\n\nStatus: " + leaveStatus(submitted.getStatus()),
                        "CHAT", List.of(), false);
            } catch (PendingLeaveExpiredException ex) {
                toolRegistry.cancelPendingLeave(conversationId, context);
                return saveResponse(conversationId, context,
                        "The prepared leave request expired before it was submitted. Nothing was submitted. "
                                + "Please prepare it again and confirm within "
                                + PendingLeaveExpiry.VALIDITY.toMinutes() + " minutes.",
                        "CHAT", List.of(), false);
            } catch (AssistantToolException ex) {
                toolRegistry.cancelPendingLeave(conversationId, context);
                String message = ex.isForbidden()
                        ? "You don't have permission to apply for leave for yourself."
                        : "I couldn't submit that leave request. Please prepare it again and confirm once more.";
                return saveResponse(conversationId, context, message, "ERROR", List.of(), false);
            } catch (RuntimeException ex) {
                log.warn("Vettri assistant leave submission failed");
                toolRegistry.cancelPendingLeave(conversationId, context);
                return saveResponse(conversationId, context,
                        "I couldn't submit that leave request. Please review the details and try again.",
                        "ERROR", List.of(), false);
            }
        }
        if (priorPending != null) {
            conversationStore.clearPendingLeave(conversationId, context.companyId(), context.userId());
        }

        if (isDeviceMappingGuidanceRequest(request.message())) {
            AssistantToolRegistry.DeviceMappingGuidance guidance = toolRegistry.deviceMappingGuidance(context);
            List<AssistantChatResponse.AssistantAction> actions = guidance.action() == null
                    ? List.of() : List.of(guidance.action());
            return saveResponse(conversationId, context, guidance.message(), "CHAT", actions, false);
        }

        boolean leaveDetailsReply = priorPending != null && !isLeaveGuidanceQuestion(request.message())
                && (containsDateExpression(request.message())
                || LEAVE_MODIFICATION.matcher(request.message()).matches()
                || request.message().toLowerCase(Locale.ROOT).contains("leave"));
        if (isLeaveActionRequest(request.message())
                && !isLeaveGuidanceQuestion(request.message())
                || leaveDetailsReply) {
            AssistantToolRegistry.ToolResult prepared = toolRegistry.prepareLeaveRequest(
                    conversationId, request.message(), contextResolver.resolve(), priorPending);
            return saveResponse(conversationId, context, prepared.message(), "CHAT",
                    prepared.action() == null ? List.of() : List.of(prepared.action()), false);
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
        List<AiProvider.ModelTool> availableTools = new ArrayList<>(toolRegistry.availableTools(context));
        if (isLeaveGuidanceQuestion(request.message())) {
            availableTools.removeIf(tool -> "prepare_leave_request".equals(tool.name()));
        }
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
                        AssistantToolRegistry.ToolResult result = "prepare_leave_request".equals(toolCall.name())
                                ? toolRegistry.execute(toolCall.name(), toolCall.arguments(), contextResolver.resolve(),
                                        conversationId, request.message(), priorPending)
                                : toolRegistry.execute(toolCall.name(), toolCall.arguments(), contextResolver.resolve());
                        if (result.message() != null) {
                            return saveResponse(conversationId, context, result.message(), "CHAT",
                                    result.action() == null ? List.of() : List.of(result.action()), false);
                        }
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

    private boolean isLeaveActionRequest(String message) {
        return LEAVE_ACTION_REQUEST.matcher(message).matches();
    }

    private boolean isLeaveGuidanceQuestion(String message) {
        return LEAVE_GUIDANCE_QUESTION.matcher(message).matches();
    }

    private boolean containsDateExpression(String message) {
        return Pattern.compile(
                "(?i)\\b(?:today|tomorrow|day after tomorrow|next\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|"
                        + "(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|\\d{1,4}[-/]\\d{1,2}[-/]\\d{1,4}|"
                        + "\\d{1,2}(?:st|nd|rd|th)?\\s+(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|"
                        + "jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?|"
                        + "(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|"
                        + "sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+\\d{1,2}))\\b")
                .matcher(message).find();
    }

    private String formatLeavePeriod(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);
        String start = startDate.format(formatter);
        return startDate.equals(endDate) ? start : start + " to " + endDate.format(formatter);
    }

    private String leaveStatus(String status) {
        return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "Pending approval";
            case "APPROVED" -> "Approved";
            case "REJECTED" -> "Rejected";
            case "CANCELLED" -> "Cancelled";
            default -> "Status: " + (status == null ? "Unavailable" : status);
        };
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
                actions.stream().anyMatch(action -> "LEAVE_CONFIRMATION".equals(action.type())),
                aiEnhanced,
                conversationId.toString()
        );
    }
}
