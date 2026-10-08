package com.haodaone.assistant;

public class AssistantToolException extends RuntimeException {
    private final boolean forbidden;

    public AssistantToolException(boolean forbidden) {
        super(forbidden ? "You don't have access to that information." : "I couldn't retrieve that information right now.");
        this.forbidden = forbidden;
    }

    public boolean isForbidden() {
        return forbidden;
    }
}
