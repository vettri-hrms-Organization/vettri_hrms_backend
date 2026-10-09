package com.haodaone.assistant.dto;

import java.util.List;
import java.util.Map;

public record AssistantChatResponse(
        String message,
        String intent,
        String mode,
        List<AssistantAction> actions,
        List<String> suggestions,
        boolean requiresConfirmation,
        boolean aiEnhanced,
        String conversationId
) {
    public AssistantChatResponse(
            String message,
            String intent,
            String mode,
            List<AssistantAction> actions,
            List<String> suggestions,
            boolean requiresConfirmation,
            boolean aiEnhanced
    ) {
        this(message, intent, mode, actions, suggestions, requiresConfirmation, aiEnhanced, null);
    }

    public record AssistantAction(String type, String label, String route, Map<String, Object> data) {
        public AssistantAction(String type, String label, String route) {
            this(type, label, route, Map.of());
        }

        public AssistantAction {
            data = data == null ? Map.of() : Map.copyOf(data);
        }
    }
}
