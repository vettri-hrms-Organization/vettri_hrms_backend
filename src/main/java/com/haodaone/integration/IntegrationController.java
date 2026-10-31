package com.haodaone.integration;

import com.haodaone.security.CustomUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Integrations admin (Slack/Teams, webhooks, API keys) and the personal calendar link. */
@RestController
public class IntegrationController {

    private final IntegrationService integrations;
    private final CalendarFeedService calendar;

    public IntegrationController(IntegrationService integrations, CalendarFeedService calendar) {
        this.integrations = integrations;
        this.calendar = calendar;
    }

    @GetMapping("/api/integrations/chat")
    @PreAuthorize("isAuthenticated()")
    public IntegrationService.ChatSettings chat() { return integrations.chat(); }

    @PutMapping("/api/integrations/chat")
    @PreAuthorize("isAuthenticated()")
    public IntegrationService.ChatSettings saveChat(@RequestBody IntegrationService.ChatRequest r, @AuthenticationPrincipal CustomUserPrincipal p) {
        return integrations.saveChat(r, name(p));
    }

    @PostMapping("/api/integrations/chat/test")
    @PreAuthorize("isAuthenticated()")
    public void testChat(@AuthenticationPrincipal CustomUserPrincipal p) { integrations.testChat(name(p)); }

    @GetMapping("/api/integrations/webhooks")
    @PreAuthorize("isAuthenticated()")
    public List<IntegrationService.WebhookDTO> webhooks() { return integrations.webhooks(); }

    @PostMapping("/api/integrations/webhooks")
    @PreAuthorize("isAuthenticated()")
    public IntegrationService.WebhookDTO createWebhook(@RequestBody IntegrationService.WebhookRequest r, @AuthenticationPrincipal CustomUserPrincipal p) {
        return integrations.createWebhook(r, name(p));
    }

    @PutMapping("/api/integrations/webhooks/{id}")
    @PreAuthorize("isAuthenticated()")
    public IntegrationService.WebhookDTO updateWebhook(@PathVariable Long id, @RequestBody IntegrationService.WebhookRequest r, @AuthenticationPrincipal CustomUserPrincipal p) {
        return integrations.updateWebhook(id, r, name(p));
    }

    @DeleteMapping("/api/integrations/webhooks/{id}")
    @PreAuthorize("isAuthenticated()")
    public void deleteWebhook(@PathVariable Long id, @AuthenticationPrincipal CustomUserPrincipal p) { integrations.deleteWebhook(id, name(p)); }

    @PostMapping("/api/integrations/webhooks/{id}/test")
    @PreAuthorize("isAuthenticated()")
    public IntegrationService.WebhookDTO testWebhook(@PathVariable Long id) { return integrations.testWebhook(id); }

    @GetMapping("/api/integrations/api-keys")
    @PreAuthorize("isAuthenticated()")
    public List<IntegrationService.ApiKeyDTO> keys() { return integrations.keys(); }

    @PostMapping("/api/integrations/api-keys")
    @PreAuthorize("isAuthenticated()")
    public IntegrationService.ApiKeyDTO createKey(@RequestBody IntegrationService.ApiKeyRequest r, @AuthenticationPrincipal CustomUserPrincipal p) {
        return integrations.createKey(r, name(p));
    }

    @PostMapping("/api/integrations/api-keys/{id}/revoke")
    @PreAuthorize("isAuthenticated()")
    public IntegrationService.ApiKeyDTO revokeKey(@PathVariable Long id, @AuthenticationPrincipal CustomUserPrincipal p) { return integrations.revokeKey(id, name(p)); }

    // ---------------------------------------------------------------- calendar

    @GetMapping("/api/calendar/link")
    @PreAuthorize("isAuthenticated()")
    public CalendarFeedService.Link calendarLink() { return calendar.myLink(); }

    @PostMapping("/api/calendar/link")
    @PreAuthorize("isAuthenticated()")
    public CalendarFeedService.Link resetCalendarLink() { return calendar.reset(); }

    @DeleteMapping("/api/calendar/link")
    @PreAuthorize("isAuthenticated()")
    public void removeCalendarLink() { calendar.remove(); }

    /** Public (token in the URL): calendar apps fetch this without logging in. */
    @GetMapping("/api/calendar/feed/{token}.ics")
    public ResponseEntity<String> feed(@PathVariable String token) {
        String ics = calendar.render(token);
        if (ics == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Calendar link not found - create a new one in Vettri HRMS.");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .header("Cache-Control", "private, max-age=900")
                .header("X-Robots-Tag", "noindex")
                .body(ics);
    }

    private static String name(CustomUserPrincipal principal) {
        if (principal == null || principal.getUser() == null) return "Admin";
        String full = principal.getUser().getFullName();
        return full == null || full.isBlank() ? principal.getUsername() : full;
    }
}
