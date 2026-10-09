package com.haodaone.assistant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AssistantConversationStore {
    private static final int MAX_TITLE_LENGTH = 200;

    private final JdbcTemplate jdbcTemplate;

    public AssistantConversationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public UUID create(long companyId, long userId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO assistant_conversation (id, company_id, user_id, title) VALUES (?, ?, ?, ?)",
                id, companyId, userId, "New conversation"
        );
        return id;
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list(long companyId, long userId) {
        return jdbcTemplate.query(
                """
                SELECT id, title, created_at, updated_at
                FROM assistant_conversation
                WHERE company_id = ? AND user_id = ? AND archived = FALSE
                ORDER BY updated_at DESC
                LIMIT 50
                """,
                this::mapSummary,
                companyId, userId
        );
    }

    @Transactional(readOnly = true)
    public Optional<ConversationSnapshot> get(UUID id, long companyId, long userId) {
        List<ConversationSummary> summaries = jdbcTemplate.query(
                """
                SELECT id, title, created_at, updated_at
                FROM assistant_conversation
                WHERE id = ? AND company_id = ? AND user_id = ? AND archived = FALSE
                """,
                this::mapSummary,
                id, companyId, userId
        );
        if (summaries.isEmpty()) return Optional.empty();
        List<StoredMessage> messages = jdbcTemplate.query(
                """
                SELECT role, content, created_at
                FROM assistant_message
                WHERE conversation_id = ?
                ORDER BY sequence_number DESC
                LIMIT 100
                """,
                (rs, rowNum) -> new StoredMessage(
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getObject("created_at", LocalDateTime.class)
                ),
                id
        );
        java.util.Collections.reverse(messages);
        return Optional.of(new ConversationSnapshot(summaries.get(0), messages));
    }

    @Transactional
    public boolean append(UUID id, long companyId, long userId, String role, String content) {
        if (!"USER".equals(role) && !"ASSISTANT".equals(role)) {
            throw new IllegalArgumentException("Unsupported assistant message role");
        }
        int updated = jdbcTemplate.update(
                """
                UPDATE assistant_conversation
                SET updated_at = CURRENT_TIMESTAMP,
                    title = CASE WHEN title = 'New conversation' AND ? = 'USER'
                        THEN ? ELSE title END
                WHERE id = ? AND company_id = ? AND user_id = ? AND archived = FALSE
                """,
                role, safeTitle(content), id, companyId, userId
        );
        if (updated == 0) return false;

        Integer lastSequence = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sequence_number), 0) FROM assistant_message WHERE conversation_id = ?",
                Integer.class,
                id
        );
        jdbcTemplate.update(
                """
                INSERT INTO assistant_message (conversation_id, sequence_number, role, content)
                VALUES (?, ?, ?, ?)
                """,
                id, (lastSequence == null ? 0 : lastSequence) + 1, role, content
        );
        return true;
    }

    @Transactional
    public boolean archive(UUID id, long companyId, long userId) {
        int archived = jdbcTemplate.update(
                """
                UPDATE assistant_conversation
                SET archived = TRUE, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND company_id = ? AND user_id = ? AND archived = FALSE
                """,
                id, companyId, userId
        );
        if (archived == 1) {
            clearPendingLeave(id, companyId, userId);
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public Optional<PendingLeaveAction> pendingLeave(UUID id, long companyId, long userId) {
        List<PendingLeaveAction> actions = jdbcTemplate.query(
                """
                SELECT employee_id, leave_type_id, leave_type_name, start_date, end_date, reason,
                       requested_days, remaining_days, is_ready, created_at
                FROM assistant_pending_leave_action
                WHERE conversation_id = ? AND company_id = ? AND user_id = ?
                """,
                (rs, rowNum) -> new PendingLeaveAction(
                        rs.getLong("employee_id"),
                        (Long) rs.getObject("leave_type_id"),
                        rs.getString("leave_type_name"),
                        rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class),
                        rs.getString("reason"),
                        (Double) rs.getObject("requested_days"),
                        (Double) rs.getObject("remaining_days"),
                        rs.getBoolean("is_ready"),
                        rs.getObject("created_at", LocalDateTime.class)
                ),
                id, companyId, userId
        );
        return actions.stream().findFirst();
    }

    @Transactional
    public Optional<PendingLeaveAction> lockPendingLeave(UUID id, long companyId, long userId) {
        List<PendingLeaveAction> actions = jdbcTemplate.query(
                """
                SELECT employee_id, leave_type_id, leave_type_name, start_date, end_date, reason,
                       requested_days, remaining_days, is_ready, created_at
                FROM assistant_pending_leave_action
                WHERE conversation_id = ? AND company_id = ? AND user_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new PendingLeaveAction(
                        rs.getLong("employee_id"),
                        (Long) rs.getObject("leave_type_id"),
                        rs.getString("leave_type_name"),
                        rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class),
                        rs.getString("reason"),
                        (Double) rs.getObject("requested_days"),
                        (Double) rs.getObject("remaining_days"),
                        rs.getBoolean("is_ready"),
                        rs.getObject("created_at", LocalDateTime.class)
                ),
                id, companyId, userId
        );
        return actions.stream().findFirst();
    }

    @Transactional
    public void savePendingLeave(
            UUID id,
            long companyId,
            long userId,
            PendingLeaveAction action
    ) {
        clearPendingLeave(id, companyId, userId);
        jdbcTemplate.update(
                """
                INSERT INTO assistant_pending_leave_action
                    (conversation_id, company_id, user_id, employee_id, leave_type_id, leave_type_name,
                     start_date, end_date, reason, requested_days, remaining_days, is_ready, created_at)
                SELECT id, company_id, user_id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                FROM assistant_conversation
                WHERE id = ? AND company_id = ? AND user_id = ? AND archived = FALSE
                """,
                action.employeeId(), action.leaveTypeId(), action.leaveTypeName(),
                action.startDate(), action.endDate(), action.reason(),
                action.requestedDays(), action.remainingDays(), action.ready(), action.createdAt(),
                id, companyId, userId
        );
    }

    @Transactional
    public void clearPendingLeave(UUID id, long companyId, long userId) {
        jdbcTemplate.update(
                "DELETE FROM assistant_pending_leave_action WHERE conversation_id = ? AND company_id = ? AND user_id = ?",
                id, companyId, userId
        );
    }

    private String safeTitle(String content) {
        String title = content.replaceAll("\\s+", " ").strip();
        return title.length() <= MAX_TITLE_LENGTH ? title : title.substring(0, MAX_TITLE_LENGTH);
    }

    private ConversationSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new ConversationSummary(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
        );
    }

    public record ConversationSummary(UUID id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record StoredMessage(String role, String content, LocalDateTime createdAt) {}
    public record ConversationSnapshot(ConversationSummary conversation, List<StoredMessage> messages) {}
    public record PendingLeaveAction(
            long employeeId,
            Long leaveTypeId,
            String leaveTypeName,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            Double requestedDays,
            Double remainingDays,
            boolean ready,
            LocalDateTime createdAt
    ) {}
}
