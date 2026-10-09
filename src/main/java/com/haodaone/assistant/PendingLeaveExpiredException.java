package com.haodaone.assistant;

final class PendingLeaveExpiredException extends RuntimeException {
    PendingLeaveExpiredException() {
        super("The prepared leave request has expired.");
    }
}
