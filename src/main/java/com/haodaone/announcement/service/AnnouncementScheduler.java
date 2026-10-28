package com.haodaone.announcement.service;

import com.haodaone.announcement.repository.AnnouncementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Every 5 minutes (IST): publishes SCHEDULED announcements whose time has
 * come and sends any audience notifications not yet sent. Both steps are
 * idempotent (atomic conditional updates), so overlapping runs or multiple
 * instances can't double-publish or double-notify. Works across all
 * companies - repository queries carry the company id explicitly.
 */
@Component
public class AnnouncementScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementService announcementService;
    private final AnnouncementNotifier announcementNotifier;

    public AnnouncementScheduler(AnnouncementRepository announcementRepository,
                                 AnnouncementService announcementService,
                                 AnnouncementNotifier announcementNotifier) {
        this.announcementRepository = announcementRepository;
        this.announcementService = announcementService;
        this.announcementNotifier = announcementNotifier;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Kolkata")
    public void publishDueAnnouncements() {
        LocalDateTime now = LocalDateTime.now(IST);
        try {
            for (Long id : announcementRepository.findDueScheduledIds(now)) {
                try {
                    if (announcementService.publishScheduled(id, now)) {
                        log.info("Published scheduled announcement {}", id);
                    }
                } catch (RuntimeException ex) {
                    log.error("Could not publish scheduled announcement {}", id, ex);
                }
            }
            for (Long id : announcementRepository.findPublishedUnnotifiedIds(now)) {
                try {
                    int notified = announcementNotifier.dispatchPublished(id);
                    if (notified > 0) log.info("Notified {} employee(s) about announcement {}", notified, id);
                } catch (RuntimeException ex) {
                    log.error("Could not notify audience for announcement {}", id, ex);
                }
            }
        } catch (RuntimeException ex) {
            log.error("Announcement publishing sweep failed", ex);
        }
    }
}
