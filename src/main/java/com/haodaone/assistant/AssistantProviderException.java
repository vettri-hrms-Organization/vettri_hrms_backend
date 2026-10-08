package com.haodaone.assistant;

public class AssistantProviderException extends RuntimeException {
    public AssistantProviderException() {
        super("Assistant provider unavailable");
    }
}
