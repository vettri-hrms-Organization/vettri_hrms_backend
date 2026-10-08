package com.haodaone.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssistantChatRequest(
        @NotBlank @Size(max = 2000) String message,
        @Size(max = 80) String conversationId,
        @Valid @Size(max = 12) List<ConversationTurn> history
) {
    public record ConversationTurn(
                @NotBlank @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 2000) String content
    ) {}
}
