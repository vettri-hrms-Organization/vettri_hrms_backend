package com.haodaone.assistant;

import com.haodaone.assistant.dto.AssistantChatRequest;
import com.haodaone.assistant.dto.AssistantChatResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assistant")
@PreAuthorize("isAuthenticated()")
public class AssistantController {
    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public AssistantChatResponse chat(@Valid @RequestBody AssistantChatRequest request) {
        return assistantService.chat(request);
    }

    @GetMapping("/conversations")
    public List<AssistantConversationStore.ConversationSummary> conversations() {
        return assistantService.conversations();
    }

    @GetMapping("/conversations/{id}")
    public AssistantConversationStore.ConversationSnapshot conversation(@PathVariable UUID id) {
        return assistantService.conversation(id);
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        assistantService.archive(id);
        return ResponseEntity.noContent().build();
    }
}
