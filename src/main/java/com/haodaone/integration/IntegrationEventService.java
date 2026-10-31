package com.haodaone.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.auth.service.MfaSecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fans business events out to Slack / Teams channels and customer webhooks. Publishing never blocks or fails the
 * caller: delivery runs after the surrounding transaction commits, on a small background pool, with a 5 s timeout.
 * Webhook payloads are signed: header X-Vettri-Signature = "sha256=" + HMAC-SHA256(secret, timestamp + "." + body).
 */
@Service
public class IntegrationEventService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationEventService.class);

    public static final String LEAVE_APPROVED = "LEAVE_APPROVED";
    public static final String EMPLOYEE_JOINED = "EMPLOYEE_JOINED";
    public static final String KUDOS_POSTED = "KUDOS_POSTED";
    public static final String ANNOUNCEMENT_PUBLISHED = "ANNOUNCEMENT_PUBLISHED";
    public static final String PAYROLL_PROCESSED = "PAYROLL_PROCESSED";
    public static final String HELPDESK_TICKET_RAISED = "HELPDESK_TICKET_RAISED";
    public static final List<String> EVENTS = List.of(LEAVE_APPROVED, EMPLOYEE_JOINED, KUDOS_POSTED, ANNOUNCEMENT_PUBLISHED, PAYROLL_PROCESSED, HELPDESK_TICKET_RAISED);
    public static final List<String> DEFAULT_CHAT_EVENTS = List.of(KUDOS_POSTED, ANNOUNCEMENT_PUBLISHED, EMPLOYEE_JOINED);

    private final IntegrationSettingsRepository settingsRepository;
    private final WebhookEndpointRepository endpoints;
    private final MfaSecretCipher cipher;
    private final OutboundUrlGuard guard;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "integration-events");
        t.setDaemon(true);
        return t;
    });

    public IntegrationEventService(IntegrationSettingsRepository settingsRepository, WebhookEndpointRepository endpoints, MfaSecretCipher cipher,
                                   OutboundUrlGuard guard, ObjectMapper objectMapper, TransactionTemplate tx) {
        this.settingsRepository = settingsRepository;
        this.endpoints = endpoints;
        this.cipher = cipher;
        this.guard = guard;
        this.objectMapper = objectMapper;
        this.tx = tx;
    }

    /** A business event. {@code text} is the human sentence for chat; {@code data} is the webhook payload. */
    public record Event(Long companyId, String type, String text, Map<String, Object> data) { }

    public void publish(Long companyId, String type, String text, Map<String, Object> data) {
        if (companyId == null) return;
        Event event = new Event(companyId, type, text, data == null ? Map.of() : data);
        Runnable send = () -> pool.submit(() -> deliver(event));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    void deliver(Event e) {
        try {
            tx.executeWithoutResult(status -> {
                settingsRepository.findFirstByCompanyIdAndDeletedFalse(e.companyId()).ifPresent(s -> deliverChat(s, e));
                for (WebhookEndpoint w : endpoints.findAllByCompanyIdAndDeletedFalseOrderByIdAsc(e.companyId())) {
                    if (w.isActive() && events(w.getEventsJson()).contains(e.type())) deliverWebhook(w, e);
                }
            });
        } catch (Exception ex) {
            log.warn("Integration delivery failed for {} ({}): {}", e.type(), e.companyId(), ex.getMessage());
        }
    }

    private void deliverChat(IntegrationSettings s, Event e) {
        if (!events(s.getEventsJson()).contains(e.type())) return;
        String error = null;
        for (String[] target : new String[][]{{s.getSlackWebhookEnc(), "SLACK"}, {s.getTeamsWebhookEnc(), "TEAMS"}}) {
            if (target[0] == null) continue;
            try {
                String url = new String(cipher.decrypt(target[0]), StandardCharsets.UTF_8);
                Map<String, Object> body = "SLACK".equals(target[1]) ? Map.of("text", e.text())
                        : Map.of("@type", "MessageCard", "@context", "https://schema.org/extensions", "summary", e.text(), "text", e.text());
                int code = post(guard.check(url, OutboundUrlGuard.Kind.valueOf(target[1])), objectMapper.writeValueAsString(body), Map.of());
                if (code >= 300) error = target[1] + " answered HTTP " + code;
            } catch (Exception ex) {
                error = target[1] + ": " + ex.getMessage();
            }
        }
        s.setLastPostedAt(LocalDateTime.now());
        s.setLastError(error == null ? null : error.length() > 300 ? error.substring(0, 300) : error);
        settingsRepository.save(s);
    }

    void deliverWebhook(WebhookEndpoint w, Event e) {
        Integer code = null;
        String error = null;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", UUID.randomUUID().toString());
            payload.put("event", e.type());
            payload.put("occurredAt", Instant.now().toString());
            payload.put("data", e.data());
            String body = objectMapper.writeValueAsString(payload);
            String ts = String.valueOf(Instant.now().getEpochSecond());
            String secret = new String(cipher.decrypt(w.getSecretEnc()), StandardCharsets.UTF_8);
            code = post(guard.check(w.getUrl(), OutboundUrlGuard.Kind.WEBHOOK), body,
                    Map.of("X-Vettri-Event", e.type(), "X-Vettri-Timestamp", ts, "X-Vettri-Signature", "sha256=" + sign(secret, ts + "." + body)));
            if (code >= 300) error = "HTTP " + code;
        } catch (Exception ex) {
            error = ex.getMessage();
        }
        w.setLastStatus(code);
        w.setLastError(error == null ? null : error.length() > 300 ? error.substring(0, 300) : error);
        w.setLastDeliveryAt(LocalDateTime.now());
        w.setFailureCount(error == null ? 0 : w.getFailureCount() + 1);
        if (w.getFailureCount() >= 20) w.setActive(false);
        endpoints.save(w);
    }

    private int post(URI uri, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json").header("User-Agent", "VettriHRMS-Webhooks/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(b::header);
        return http.send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    static String sign(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    List<String> events(String json) {
        try {
            return json == null ? List.of() : objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception ex) {
            return List.of();
        }
    }
}
