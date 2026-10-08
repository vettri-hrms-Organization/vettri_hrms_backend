package com.haodaone.notifications.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    public NotificationService(JdbcTemplate jdbcTemplate, EmailService emailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
    }

    public Map<String, Object> inbox(int page, int size, String type) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        long companyId = currentCompanyId();
        long userId = currentUserId();
        String normalizedType = type == null || type.isBlank() ? null : type.trim().toUpperCase();
        String typeClause = normalizedType == null ? "" : " AND type = ?";
        List<Object> args = new ArrayList<>(List.of(companyId, userId));
        if (normalizedType != null) args.add(normalizedType);
        args.add(safeSize);
        args.add((long) safePage * safeSize);
        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT id, type, title, message, entity_type, entity_id, priority, created_at, read_at
                FROM notification
                WHERE company_id = ? AND recipient_user_id = ? AND deleted = FALSE
                """ + typeClause + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", args.toArray());

        List<Object> countArgs = new ArrayList<>(List.of(companyId, userId));
        if (normalizedType != null) countArgs.add(normalizedType);
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notification
                WHERE company_id = ? AND recipient_user_id = ? AND deleted = FALSE
                """ + typeClause, Long.class, countArgs.toArray());
        Long unread = unreadCount(companyId, userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("totalElements", total == null ? 0L : total);
        result.put("totalPages", total == null || total == 0 ? 0 : (total + safeSize - 1) / safeSize);
        result.put("unreadCount", unread);
        return result;
    }

    public List<Map<String, Object>> legacyList() {
        return jdbcTemplate.queryForList("""
                SELECT id, type, title, message, entity_type, entity_id, priority, created_at, read_at
                FROM notification
                WHERE company_id = ? AND recipient_user_id = ? AND deleted = FALSE
                ORDER BY created_at DESC, id DESC
                """, currentCompanyId(), currentUserId());
    }

    public long unreadCount() {
        return unreadCount(currentCompanyId(), currentUserId());
    }

    private long unreadCount(long companyId, long userId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notification
                WHERE company_id = ? AND recipient_user_id = ? AND read_at IS NULL AND deleted = FALSE
                """, Long.class, companyId, userId);
        return count == null ? 0 : count;
    }

    public void markRead(long id) {
        int changed = jdbcTemplate.update("""
                UPDATE notification SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP), updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND company_id = ? AND recipient_user_id = ? AND deleted = FALSE
                """, id, currentCompanyId(), currentUserId());
        if (changed == 0) throw new BadRequestException("Notification not found.");
    }

    public int markAllRead() {
        return jdbcTemplate.update("""
                UPDATE notification SET read_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE company_id = ? AND recipient_user_id = ? AND read_at IS NULL AND deleted = FALSE
                """, currentCompanyId(), currentUserId());
    }

    public Map<String, Object> emailPreferences() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT workflow_email_enabled FROM notification_email_preference
                WHERE company_id = ? AND user_id = ? AND deleted = FALSE
                """, currentCompanyId(), currentUserId());
        boolean workflowEnabled = rows.isEmpty() || Boolean.TRUE.equals(rows.get(0).get("workflow_email_enabled"));
        return Map.of("workflowEmailEnabled", workflowEnabled, "securityEmailEnabled", true);
    }

    public Map<String, Object> updateEmailPreferences(Boolean workflowEmailEnabled) {
        if (workflowEmailEnabled == null) {
            throw new BadRequestException("workflowEmailEnabled is required.");
        }
        long companyId = currentCompanyId();
        long userId = currentUserId();
        jdbcTemplate.update("""
                INSERT INTO notification_email_preference
                    (company_id, user_id, workflow_email_enabled, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (company_id, user_id) DO UPDATE
                SET workflow_email_enabled = EXCLUDED.workflow_email_enabled,
                    updated_at = CURRENT_TIMESTAMP,
                    deleted = FALSE,
                    deleted_at = NULL
                """, companyId, userId, workflowEmailEnabled);
        return emailPreferences();
    }

    public void notifyUser(Long companyId, Long recipientUserId, String type, String title, String message,
                           String entityType, Long entityId, String priority) {
        if (companyId == null || recipientUserId == null) return;
        List<Map<String, Object>> recipients = jdbcTemplate.queryForList("""
                SELECT id, email, full_name FROM app_user
                WHERE id = ? AND company_id = ? AND active = TRUE AND deleted = FALSE
                """, recipientUserId, companyId);
        if (recipients.isEmpty()) return;

        String deduplicationKey = entityType == null || entityId == null
                ? null : type + ":" + entityType + ":" + entityId;
        int inserted = jdbcTemplate.update("""
                INSERT INTO notification
                    (company_id, recipient_user_id, type, title, message, entity_type, entity_id, priority, deduplication_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (company_id, recipient_user_id, deduplication_key)
                    WHERE deduplication_key IS NOT NULL DO NOTHING
                """, companyId, recipientUserId, type, title, message, entityType, entityId,
                priority == null ? "NORMAL" : priority, deduplicationKey);
        if (inserted == 0) return;

        Map<String, Object> recipient = recipients.get(0);
        Runnable sendEmail = () -> sendWorkflowEmail(recipient, companyId, recipientUserId, type, title, message);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendEmail.run();
                }
            });
        } else {
            sendEmail.run();
        }
    }

    public void notifyPermissionRecipients(Long companyId, Long targetEmployeeId, String permissionCode,
                                           String type, String title, String message, String entityType, Long entityId) {
        if (companyId == null || targetEmployeeId == null) return;
        Set<Long> recipientIds = new LinkedHashSet<>();
        jdbcTemplate.queryForList("""
                SELECT DISTINCT u.id
                FROM app_user u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN role r ON r.id = ur.role_id AND r.deleted = FALSE
                JOIN role_permissions rp ON rp.role_id = r.id
                JOIN permission p ON p.id = rp.permission_id AND p.deleted = FALSE
                JOIN role_permission_scope rps ON rps.role_id = r.id AND rps.permission_id = p.id
                JOIN employee recipient ON recipient.user_id = u.id AND recipient.company_id = u.company_id AND recipient.deleted = FALSE
                JOIN employee target ON target.id = ? AND target.company_id = ? AND target.deleted = FALSE
                WHERE u.company_id = ? AND u.active = TRUE AND u.deleted = FALSE AND p.code = ?
                  AND rps.scope <> 'CUSTOM'
                  AND (rps.valid_from IS NULL OR rps.valid_from <= CURRENT_TIMESTAMP)
                  AND (rps.valid_until IS NULL OR rps.valid_until > CURRENT_TIMESTAMP)
                  AND (
                    (rps.scope = 'ORGANIZATION')
                    OR (rps.scope = 'SELF' AND recipient.id = target.id)
                    OR (rps.scope = 'TEAM' AND recipient.team_id IS NOT NULL AND recipient.team_id = target.team_id)
                    OR (rps.scope = 'DEPARTMENT' AND recipient.department_id IS NOT NULL AND recipient.department_id = target.department_id)
                  )
                """, targetEmployeeId, companyId, companyId, permissionCode)
                .forEach(row -> recipientIds.add(((Number) row.get("id")).longValue()));
        jdbcTemplate.queryForList("""
                SELECT DISTINCT u.id
                FROM user_permission_grant g
                JOIN app_user u ON u.id = g.user_id AND u.company_id = g.company_id
                JOIN permission p ON p.id = g.permission_id AND p.deleted = FALSE
                JOIN employee recipient ON recipient.user_id = u.id AND recipient.company_id = u.company_id AND recipient.deleted = FALSE
                JOIN employee target ON target.id = ? AND target.company_id = ? AND target.deleted = FALSE
                WHERE g.company_id = ? AND g.revoked_at IS NULL AND g.deleted = FALSE
                  AND u.active = TRUE AND u.deleted = FALSE AND p.code = ? AND g.scope <> 'CUSTOM'
                  AND (
                    (g.scope = 'ORGANIZATION')
                    OR (g.scope = 'SELF' AND recipient.id = target.id)
                    OR (g.scope = 'TEAM' AND recipient.team_id IS NOT NULL AND recipient.team_id = target.team_id)
                    OR (g.scope = 'DEPARTMENT' AND recipient.department_id IS NOT NULL AND recipient.department_id = target.department_id)
                  )
                """, targetEmployeeId, companyId, companyId, permissionCode)
                .forEach(row -> recipientIds.add(((Number) row.get("id")).longValue()));

        recipientIds.forEach(recipientId -> notifyUser(companyId, recipientId, type, title, message,
                entityType, entityId, "NORMAL"));
    }

    private void sendWorkflowEmail(Map<String, Object> recipient, long companyId, long userId,
                                   String type, String title, String message) {
        String email = (String) recipient.get("email");
        if (email == null || email.isBlank()) return;
        List<Map<String, Object>> preferences = jdbcTemplate.queryForList("""
                SELECT workflow_email_enabled FROM notification_email_preference
                WHERE company_id = ? AND user_id = ? AND deleted = FALSE
                """, companyId, userId);
        boolean criticalSecurityAlert = "SECURITY_ALERT".equals(type);
        if (!criticalSecurityAlert && !preferences.isEmpty()
                && !Boolean.TRUE.equals(preferences.get(0).get("workflow_email_enabled"))) return;
        String body = title + "\n\n" + message + "\n\nOpen Vettri HRMS to view details.";
        try {
            emailService.sendTextEmail(email, title, body, EmailService.EmailChannel.SYSTEM, null);
        } catch (MessagingException exception) {
            log.warn("Could not send notification email to user {} for notification type {}", userId, type, exception);
        }
    }

    private long currentCompanyId() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new AccessDeniedException("A company context is required.");
        CustomUserPrincipal principal = currentPrincipal();
        Long principalCompanyId = principal.getCompanyId();
        boolean superAdmin = principal.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
        if (!superAdmin && !companyId.equals(principalCompanyId)) {
            throw new AccessDeniedException("Notification access is limited to your company.");
        }
        return companyId;
    }

    private long currentUserId() {
        return currentPrincipal().getId();
    }

    private CustomUserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserPrincipal principal)) {
            throw new AccessDeniedException("Authenticated user identity is required.");
        }
        return principal;
    }
}
