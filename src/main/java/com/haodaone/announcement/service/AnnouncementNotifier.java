package com.haodaone.announcement.service;

import com.haodaone.announcement.entity.Announcement;
import com.haodaone.announcement.repository.AnnouncementRepository;
import com.haodaone.notifications.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Fans announcement notifications out to an audience in chunks. Each chunk
 * runs in its own transaction, so NotificationService's after-commit emails
 * go out per chunk instead of piling up behind one giant transaction, and a
 * failure in one chunk doesn't roll back the others. Call this outside any
 * surrounding transaction (controller / scheduler).
 */
@Component
public class AnnouncementNotifier {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementNotifier.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final int CHUNK_SIZE = 100;

    private final AnnouncementRepository announcementRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate requiresNew;

    public AnnouncementNotifier(AnnouncementRepository announcementRepository,
                                NotificationService notificationService,
                                PlatformTransactionManager transactionManager) {
        this.announcementRepository = announcementRepository;
        this.notificationService = notificationService;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private record Dispatch(Long companyId, Long announcementId, String title, String message, String priority,
                            List<Long> userIds) {
    }

    /**
     * Notifies the audience of a published announcement exactly once
     * (guarded by the atomic notified_at claim). Returns recipients notified.
     */
    public int dispatchPublished(Long announcementId) {
        Dispatch dispatch = requiresNew.execute(status -> {
            if (announcementRepository.claimNotification(announcementId, LocalDateTime.now(IST)) == 0) return null;
            Announcement announcement = announcementRepository.findByIdAndDeletedFalse(announcementId).orElse(null);
            if (announcement == null) return null;
            Long companyId = announcement.getCompany().getId();
            List<Long> userIds = AnnouncementAudience.audienceOf(announcement, AnnouncementAudience.toMembers(
                            announcementRepository.findAudienceMembers(companyId, AnnouncementAudience.EXCLUDED_STATUSES)))
                    .stream().map(AnnouncementAudience.Member::userId).toList();
            String message = announcement.isRequiresAcknowledgement()
                    ? "Please read and acknowledge" + (announcement.getAcknowledgementDueDate() == null ? "."
                    : " by " + announcement.getAcknowledgementDueDate() + ".")
                    : "A new announcement has been posted. Open Announcements to read it.";
            String priority = "URGENT".equals(announcement.getCategory()) ? "HIGH" : "NORMAL";
            return new Dispatch(companyId, announcementId, announcement.getTitle(), message, priority, userIds);
        });
        if (dispatch == null) return 0;
        return notifyUsers(dispatch.companyId(), dispatch.userIds(), "ANNOUNCEMENT",
                truncate("Announcement: " + dispatch.title(), 200), dispatch.message(), "Announcement",
                dispatch.announcementId(), dispatch.priority());
    }

    /** Chunked fan-out; duplicates removed. NotificationService de-duplicates per type/entity/recipient too. */
    public int notifyUsers(Long companyId, List<Long> userIds, String type, String title, String message,
                           String entityType, Long entityId, String priority) {
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(userIds.stream().filter(Objects::nonNull).toList()));
        int sent = 0;
        for (int start = 0; start < distinct.size(); start += CHUNK_SIZE) {
            List<Long> chunk = distinct.subList(start, Math.min(distinct.size(), start + CHUNK_SIZE));
            try {
                requiresNew.executeWithoutResult(status -> chunk.forEach(userId ->
                        notificationService.notifyUser(companyId, userId, type, title, message, entityType, entityId, priority)));
                sent += chunk.size();
            } catch (RuntimeException ex) {
                log.warn("Announcement notification chunk failed for {} {} ({} recipients)", entityType, entityId, chunk.size(), ex);
            }
        }
        return sent;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
