package com.haodaone.assistant;

import com.haodaone.assistant.dto.AssistantChatRequest;

import java.util.List;
import java.util.Optional;

public interface AssistantLanguageProvider {
    boolean isConfigured();

    Optional<String> classify(
            String message,
            List<AssistantChatRequest.ConversationTurn> history,
            List<String> allowedIntentIds
    );
}
