package com.haodaone.announcement.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/** Request/response shapes for announcements. Response timestamps are IST offset date-times. */
public final class AnnouncementDtos {

    private AnnouncementDtos() {
    }

    /**
     * publishMode: DRAFT (save only), NOW (publish immediately) or SCHEDULE
     * (publish at publishAt, IST wall-clock time).
     */
    public record AnnouncementRequest(
            String title,
            String body,
            String category,
            String audienceType,
            List<Long> departmentIds,
            String publishMode,
            LocalDateTime publishAt,
            LocalDateTime expiresAt,
            Boolean pinned,
            Boolean requiresAcknowledgement,
            LocalDate acknowledgementDueDate) {
    }

    public record FeedItem(
            Long id,
            String title,
            String body,
            String category,
            boolean pinned,
            OffsetDateTime publishedAt,
            OffsetDateTime expiresAt,
            String authorName,
            boolean requiresAcknowledgement,
            LocalDate acknowledgementDueDate,
            boolean hasAttachment,
            String attachmentFileName,
            boolean read,
            OffsetDateTime acknowledgedAt) {
    }

    public record AdminItem(
            Long id,
            String title,
            String body,
            String category,
            String audienceType,
            List<Long> departmentIds,
            List<String> departmentNames,
            String status,
            boolean expired,
            OffsetDateTime publishAt,
            OffsetDateTime publishedAt,
            OffsetDateTime expiresAt,
            boolean pinned,
            boolean requiresAcknowledgement,
            LocalDate acknowledgementDueDate,
            boolean hasAttachment,
            String attachmentFileName,
            String authorName,
            OffsetDateTime createdAt,
            long audienceCount,
            long readCount,
            long acknowledgedCount) {
    }

    public record AcknowledgementRow(
            Long employeeId,
            String employeeCode,
            String employeeName,
            String departmentName,
            OffsetDateTime readAt,
            OffsetDateTime acknowledgedAt) {
    }

    public record AcknowledgementReport(
            Long announcementId,
            String title,
            boolean requiresAcknowledgement,
            LocalDate acknowledgementDueDate,
            long audienceCount,
            long readCount,
            long acknowledgedCount,
            long pendingCount,
            List<AcknowledgementRow> rows) {
    }

    public record UnreadCount(long unread, long pendingAcknowledgements) {
    }

    public record ReminderResult(int reminded) {
    }
}
