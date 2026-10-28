package com.haodaone.announcement.controller;

import com.haodaone.announcement.dto.AnnouncementDtos.AcknowledgementReport;
import com.haodaone.announcement.dto.AnnouncementDtos.AdminItem;
import com.haodaone.announcement.dto.AnnouncementDtos.AnnouncementRequest;
import com.haodaone.announcement.dto.AnnouncementDtos.FeedItem;
import com.haodaone.announcement.dto.AnnouncementDtos.ReminderResult;
import com.haodaone.announcement.dto.AnnouncementDtos.UnreadCount;
import com.haodaone.announcement.entity.Announcement;
import com.haodaone.announcement.service.AnnouncementNotifier;
import com.haodaone.announcement.service.AnnouncementService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Announcements. Any signed-in user of the company can read the feed
 * (audience rules applied in the service); administration needs
 * ANNOUNCEMENT_MANAGE at organization scope.
 *
 * Notification fan-out runs after the publishing transaction has committed
 * (service call returns first, then AnnouncementNotifier), in chunks.
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private static final String MANAGE = "@authorizationService.hasOrganizationScope('ANNOUNCEMENT_MANAGE')";

    private final AnnouncementService announcementService;
    private final AnnouncementNotifier announcementNotifier;

    public AnnouncementController(AnnouncementService announcementService, AnnouncementNotifier announcementNotifier) {
        this.announcementService = announcementService;
        this.announcementNotifier = announcementNotifier;
    }

    // ---- feed

    @GetMapping("/feed")
    @PreAuthorize("isAuthenticated()")
    public List<FeedItem> feed() {
        return announcementService.feed();
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public UnreadCount unreadCount() {
        return announcementService.unreadCount();
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public FeedItem markRead(@PathVariable Long id) {
        return announcementService.markRead(id);
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("isAuthenticated()")
    public FeedItem acknowledge(@PathVariable Long id) {
        return announcementService.acknowledge(id);
    }

    /** Audience members or announcement managers - checked in the service. */
    @GetMapping("/{id}/attachment")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InputStreamResource> attachment(@PathVariable Long id) {
        AnnouncementService.AttachmentDownload download = announcementService.attachment(id);
        MediaType type = download.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(download.contentType());
        String safeName = download.fileName().replaceAll("[\"\\r\\n\\\\]", "");
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    // ---- administration

    @GetMapping("/admin")
    @PreAuthorize(MANAGE)
    public List<AdminItem> adminList() {
        return announcementService.adminList();
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize(MANAGE)
    public AdminItem adminGet(@PathVariable Long id) {
        return announcementService.adminGet(id);
    }

    @PostMapping("/admin")
    @PreAuthorize(MANAGE)
    public ResponseEntity<AdminItem> create(@RequestBody AnnouncementRequest request) {
        AdminItem created = announcementService.create(request);
        dispatchIfPublished(created);
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize(MANAGE)
    public AdminItem update(@PathVariable Long id, @RequestBody AnnouncementRequest request) {
        AdminItem updated = announcementService.update(id, request);
        dispatchIfPublished(updated);
        return updated;
    }

    @PostMapping("/admin/{id}/publish")
    @PreAuthorize(MANAGE)
    public AdminItem publish(@PathVariable Long id) {
        AdminItem published = announcementService.publishNow(id);
        dispatchIfPublished(published);
        return published;
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/admin/{id}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGE)
    public AdminItem uploadAttachment(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return announcementService.uploadAttachment(id, file);
    }

    @DeleteMapping("/admin/{id}/attachment")
    @PreAuthorize(MANAGE)
    public AdminItem removeAttachment(@PathVariable Long id) {
        return announcementService.removeAttachment(id);
    }

    @GetMapping("/admin/{id}/acknowledgements")
    @PreAuthorize(MANAGE)
    public AcknowledgementReport acknowledgements(@PathVariable Long id) {
        return announcementService.acknowledgements(id);
    }

    @GetMapping("/admin/{id}/acknowledgements/export")
    @PreAuthorize(MANAGE)
    public ResponseEntity<byte[]> exportAcknowledgements(@PathVariable Long id) {
        byte[] body = ("﻿" + announcementService.acknowledgementsCsv(id)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"announcement-" + id + "-acknowledgements.csv\"")
                .body(body);
    }

    @PostMapping("/admin/{id}/remind")
    @PreAuthorize(MANAGE)
    public ReminderResult remind(@PathVariable Long id) {
        AnnouncementService.ReminderBatch batch = announcementService.pendingReminderRecipients(id);
        String due = batch.dueDate() == null ? "" : " by " + batch.dueDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        String title = "Reminder: acknowledge \"" + batch.title() + "\"";
        if (title.length() > 200) title = title.substring(0, 200);
        // Entity type carries the date so one reminder per day gets through NotificationService de-duplication.
        int reminded = announcementNotifier.notifyUsers(batch.companyId(), batch.userIds(), "ANNOUNCEMENT_REMINDER", title,
                "Please read and acknowledge this announcement" + due + ".",
                "AnnouncementReminder:" + LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")), batch.announcementId(), "HIGH");
        return new ReminderResult(reminded);
    }

    private void dispatchIfPublished(AdminItem item) {
        if (Announcement.STATUS_PUBLISHED.equals(item.status())) {
            announcementNotifier.dispatchPublished(item.id());
        }
    }
}
