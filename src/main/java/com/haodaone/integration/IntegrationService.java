package com.haodaone.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.auth.service.MfaSecretCipher;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.performance.suite.PerfContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Admin side of integrations: Slack/Teams channel posts, outbound webhooks and public-API keys. Admins are people with
 * EMPLOYEE_MANAGE at organization scope. Secrets (webhook URLs, signing secrets) are stored encrypted and never returned;
 * API keys are stored as SHA-256 hashes and shown once.
 */
@Service
public class IntegrationService {

    public static final List<String> SCOPES = List.of("employees:read", "leave:read", "attendance:read", "holidays:read");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IntegrationSettingsRepository settingsRepository;
    private final WebhookEndpointRepository endpoints;
    private final ApiKeyRepository apiKeys;
    private final IntegrationEventService events;
    private final OutboundUrlGuard guard;
    private final MfaSecretCipher cipher;
    private final PerfContext ctx;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IntegrationService(IntegrationSettingsRepository settingsRepository, WebhookEndpointRepository endpoints, ApiKeyRepository apiKeys,
                              IntegrationEventService events, OutboundUrlGuard guard, MfaSecretCipher cipher, PerfContext ctx,
                              AuditLogService auditLogService, ObjectMapper objectMapper, Clock applicationClock) {
        this.settingsRepository = settingsRepository;
        this.endpoints = endpoints;
        this.apiKeys = apiKeys;
        this.events = events;
        this.guard = guard;
        this.cipher = cipher;
        this.ctx = ctx;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = applicationClock;
    }

    public record ChatSettings(boolean slackConnected, boolean teamsConnected, List<String> events, List<String> availableEvents,
                               String lastError, LocalDateTime lastPostedAt) { }
    /** slackWebhookUrl / teamsWebhookUrl: null = keep, "" = disconnect, otherwise replace. */
    public record ChatRequest(String slackWebhookUrl, String teamsWebhookUrl, List<String> events) { }
    public record WebhookDTO(Long id, String url, String description, List<String> events, boolean active, Integer lastStatus, String lastError,
                             LocalDateTime lastDeliveryAt, int failureCount, String signingSecret) { }
    public record WebhookRequest(String url, String description, List<String> events, Boolean active) { }
    public record ApiKeyDTO(Long id, String name, String prefix, List<String> scopes, String createdByName, LocalDateTime createdAt,
                            LocalDateTime lastUsedAt, LocalDateTime revokedAt, LocalDate expiresOn, String key) { }
    public record ApiKeyRequest(String name, List<String> scopes, LocalDate expiresOn) { }

    // ------------------------------------------------------------------ chat

    @Transactional(readOnly = true)
    public ChatSettings chat() {
        requireAdmin();
        IntegrationSettings s = settingsRepository.findFirstByCompanyIdAndDeletedFalse(ctx.companyId()).orElse(null);
        if (s == null) return new ChatSettings(false, false, IntegrationEventService.DEFAULT_CHAT_EVENTS, IntegrationEventService.EVENTS, null, null);
        return new ChatSettings(s.getSlackWebhookEnc() != null, s.getTeamsWebhookEnc() != null, events.events(s.getEventsJson()),
                IntegrationEventService.EVENTS, s.getLastError(), s.getLastPostedAt());
    }

    @Transactional
    public ChatSettings saveChat(ChatRequest r, String actor) {
        requireAdmin();
        IntegrationSettings s = settingsRepository.findFirstByCompanyIdAndDeletedFalse(ctx.companyId()).orElseGet(() -> {
            IntegrationSettings n = new IntegrationSettings();
            n.setCompanyId(ctx.companyId());
            n.setEventsJson(write(IntegrationEventService.DEFAULT_CHAT_EVENTS));
            return n;
        });
        if (r == null) throw new BadRequestException("Nothing to save");
        if (r.slackWebhookUrl() != null) s.setSlackWebhookEnc(r.slackWebhookUrl().isBlank() ? null : encrypt(guard.check(r.slackWebhookUrl(), OutboundUrlGuard.Kind.SLACK).toString()));
        if (r.teamsWebhookUrl() != null) s.setTeamsWebhookEnc(r.teamsWebhookUrl().isBlank() ? null : encrypt(guard.check(r.teamsWebhookUrl(), OutboundUrlGuard.Kind.TEAMS).toString()));
        if (r.events() != null) s.setEventsJson(write(validEvents(r.events())));
        s.setLastError(null);
        settingsRepository.save(s);
        auditLogService.log("INTEGRATION", s.getId(), "CHAT_UPDATED", actor + " updated Slack/Teams posting (Slack " + (s.getSlackWebhookEnc() != null) + ", Teams " + (s.getTeamsWebhookEnc() != null) + ")");
        return chat();
    }

    public void testChat(String actor) {
        requireAdmin();
        events.publish(ctx.companyId(), firstChatEvent(), "Vettri HRMS is connected. " + actor + " sent this test message.", Map.of("test", true));
    }

    private String firstChatEvent() {
        return settingsRepository.findFirstByCompanyIdAndDeletedFalse(ctx.companyId()).map(s -> events.events(s.getEventsJson()))
                .filter(l -> !l.isEmpty()).map(l -> l.get(0)).orElseThrow(() -> new BadRequestException("Choose at least one event to post"));
    }

    // ------------------------------------------------------------------ webhooks

    @Transactional(readOnly = true)
    public List<WebhookDTO> webhooks() {
        requireAdmin();
        return endpoints.findAllByCompanyIdAndDeletedFalseOrderByIdAsc(ctx.companyId()).stream().map(w -> toDto(w, null)).toList();
    }

    @Transactional
    public WebhookDTO createWebhook(WebhookRequest r, String actor) {
        requireAdmin();
        if (endpoints.findAllByCompanyIdAndDeletedFalseOrderByIdAsc(ctx.companyId()).size() >= 10) throw new BadRequestException("Up to 10 webhooks per company");
        WebhookEndpoint w = new WebhookEndpoint();
        w.setCompanyId(ctx.companyId());
        apply(w, r);
        String secret = "whsec_" + token(24);
        w.setSecretEnc(encrypt(secret));
        w.setFailureCount(0);
        w = endpoints.save(w);
        auditLogService.log("WEBHOOK", w.getId(), "CREATED", actor + " added a webhook to " + host(w.getUrl()));
        return toDto(w, secret);
    }

    @Transactional
    public WebhookDTO updateWebhook(Long id, WebhookRequest r, String actor) {
        requireAdmin();
        WebhookEndpoint w = webhook(id);
        apply(w, r);
        if (Boolean.TRUE.equals(r.active())) w.setFailureCount(0);
        endpoints.save(w);
        auditLogService.log("WEBHOOK", w.getId(), "UPDATED", actor + " updated the webhook to " + host(w.getUrl()));
        return toDto(w, null);
    }

    @Transactional
    public void deleteWebhook(Long id, String actor) {
        requireAdmin();
        WebhookEndpoint w = webhook(id);
        w.setDeleted(true);
        endpoints.save(w);
        auditLogService.log("WEBHOOK", w.getId(), "DELETED", actor + " removed the webhook to " + host(w.getUrl()));
    }

    /** Sends a signed "ping" right away and returns the result. */
    @Transactional
    public WebhookDTO testWebhook(Long id) {
        requireAdmin();
        WebhookEndpoint w = webhook(id);
        events.deliverWebhook(w, new IntegrationEventService.Event(w.getCompanyId(), "PING", "Test", Map.of("message", "Hello from Vettri HRMS")));
        return toDto(w, null);
    }

    // ------------------------------------------------------------------ API keys

    @Transactional(readOnly = true)
    public List<ApiKeyDTO> keys() {
        requireAdmin();
        return apiKeys.findAllByCompanyIdAndDeletedFalseOrderByIdDesc(ctx.companyId()).stream().map(k -> toDto(k, null)).toList();
    }

    @Transactional
    public ApiKeyDTO createKey(ApiKeyRequest r, String actor) {
        requireAdmin();
        String name = PerfContext.text(r == null ? null : r.name(), 100);
        if (name == null) throw new BadRequestException("Name the key (e.g. the system that will use it)");
        List<String> scopes = r.scopes() == null ? List.of() : r.scopes().stream().distinct().toList();
        if (scopes.isEmpty() || !SCOPES.containsAll(scopes)) throw new BadRequestException("Choose what the key can read");
        if (r.expiresOn() != null && !r.expiresOn().isAfter(LocalDate.now(clock))) throw new BadRequestException("Expiry must be in the future");
        String prefix = "vk_" + token(6).substring(0, 8);
        String full = prefix + "_" + token(32);
        ApiKey k = new ApiKey();
        k.setCompanyId(ctx.companyId());
        k.setName(name);
        k.setPrefix(prefix);
        k.setKeyHash(sha256(full));
        k.setScopesJson(write(scopes));
        k.setCreatedByName(actor);
        k.setExpiresOn(r.expiresOn());
        k = apiKeys.save(k);
        auditLogService.log("API_KEY", k.getId(), "CREATED", actor + " created API key " + prefix + " (" + String.join(", ", scopes) + ")");
        return toDto(k, full);
    }

    @Transactional
    public ApiKeyDTO revokeKey(Long id, String actor) {
        requireAdmin();
        ApiKey k = apiKeys.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Key not found"));
        if (k.getRevokedAt() == null) {
            k.setRevokedAt(LocalDateTime.now(clock));
            apiKeys.save(k);
            auditLogService.log("API_KEY", k.getId(), "REVOKED", actor + " revoked API key " + k.getPrefix());
        }
        return toDto(k, null);
    }

    // ------------------------------------------------------------------ helpers

    private void apply(WebhookEndpoint w, WebhookRequest r) {
        if (r == null) throw new BadRequestException("Describe the webhook");
        if (r.url() != null) w.setUrl(guard.check(r.url(), OutboundUrlGuard.Kind.WEBHOOK).toString());
        if (w.getUrl() == null) throw new BadRequestException("Enter the URL to call");
        w.setDescription(PerfContext.text(r.description(), 200));
        if (r.events() != null) w.setEventsJson(write(validEvents(r.events())));
        if (w.getEventsJson() == null) throw new BadRequestException("Choose at least one event");
        if (r.active() != null) w.setActive(r.active());
    }

    private static List<String> validEvents(List<String> requested) {
        List<String> list = requested.stream().distinct().toList();
        if (!IntegrationEventService.EVENTS.containsAll(list)) throw new BadRequestException("Unknown event");
        return list;
    }

    private WebhookEndpoint webhook(Long id) {
        return endpoints.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Webhook not found"));
    }

    private WebhookDTO toDto(WebhookEndpoint w, String secret) {
        return new WebhookDTO(w.getId(), w.getUrl(), w.getDescription(), events.events(w.getEventsJson()), w.isActive(), w.getLastStatus(), w.getLastError(),
                w.getLastDeliveryAt(), w.getFailureCount(), secret);
    }

    private ApiKeyDTO toDto(ApiKey k, String full) {
        List<String> scopes;
        try {
            scopes = objectMapper.readValue(k.getScopesJson(), new TypeReference<List<String>>() { });
        } catch (Exception ex) {
            scopes = List.of();
        }
        return new ApiKeyDTO(k.getId(), k.getName(), k.getPrefix(), scopes, k.getCreatedByName(), k.getCreatedAt(), k.getLastUsedAt(), k.getRevokedAt(), k.getExpiresOn(), full);
    }

    private String encrypt(String value) {
        return cipher.encrypt(value.getBytes(StandardCharsets.UTF_8));
    }

    private String write(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String host(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception ex) {
            return "?";
        }
    }

    static String token(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void requireAdmin() {
        if (!ctx.hasOrgScope("EMPLOYEE_MANAGE")) throw new AccessDeniedException("Only admins can manage integrations");
    }
}
