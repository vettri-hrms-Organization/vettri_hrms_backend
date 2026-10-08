package com.haodaone.assistant.dto;

import java.util.List;

public record AssistantChatResponse(
        String message,
        String intent,
        String mode,
        List<AssistantAction> actions,
        List<String> suggestions,
        boolean requiresConfirmation,
        boolean aiEnhanced
) {
    public record AssistantAction(String type, String label, String route) {}
}
