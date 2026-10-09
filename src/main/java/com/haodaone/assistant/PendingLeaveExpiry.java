package com.haodaone.assistant;

import java.time.Duration;
import java.time.LocalDateTime;

final class PendingLeaveExpiry {
    static final Duration VALIDITY = Duration.ofMinutes(30);

    private PendingLeaveExpiry() {}

    static boolean isExpired(LocalDateTime createdAt, LocalDateTime now) {
        return createdAt == null || !now.isBefore(createdAt.plus(VALIDITY));
    }
}
