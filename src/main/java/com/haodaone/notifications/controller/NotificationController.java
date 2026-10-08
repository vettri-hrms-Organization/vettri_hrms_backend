package com.haodaone.notifications.controller;

import com.haodaone.notifications.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<Map<String, Object>> legacyList() {
        return notificationService.legacyList();
    }

    @GetMapping("/inbox")
    public Map<String, Object> inbox(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String type) {
        return notificationService.inbox(page, size, type);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unreadCount", notificationService.unreadCount());
    }

    @PatchMapping("/{id}/read")
    public void markRead(@PathVariable Long id) {
        notificationService.markRead(id);
    }

    @PatchMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", notificationService.markAllRead());
    }

    @GetMapping("/preferences")
    public Map<String, Object> emailPreferences() {
        return notificationService.emailPreferences();
    }

    @PatchMapping("/preferences")
    public Map<String, Object> updateEmailPreferences(@RequestBody EmailPreferenceRequest request) {
        return notificationService.updateEmailPreferences(request.workflowEmailEnabled());
    }

    public record EmailPreferenceRequest(Boolean workflowEmailEnabled) {}
}
