package com.haodaone.assistant;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PendingLeaveExpiryTest {
    @Test
    void proposalExpiresAtExactlyThirtyMinutes() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 9, 12, 0);

        assertThat(PendingLeaveExpiry.isExpired(createdAt, createdAt.plusMinutes(30).minusNanos(1))).isFalse();
        assertThat(PendingLeaveExpiry.isExpired(createdAt, createdAt.plusMinutes(30))).isTrue();
        assertThat(PendingLeaveExpiry.isExpired(createdAt, createdAt.plusMinutes(30).plusNanos(1))).isTrue();
    }

    @Test
    void missingOrFutureCreationTimestampIsInvalid() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 9, 12, 0);

        assertThat(PendingLeaveExpiry.isExpired(null, now)).isTrue();
        assertThat(PendingLeaveExpiry.isExpired(now.plusNanos(1), now)).isTrue();
    }
}
