package com.haodaone.notifications.whatsapp;

import com.haodaone.demo.service.DemoCompanyGuard;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Additional NotificationService channel: mapped notification types are also
 * sent as approved WhatsApp template messages when the server switch is on,
 * the company enabled the channel and that event, the company is not a demo,
 * and the recipient has an active opt-in. Runs after commit on a small
 * background pool; failures are logged (whatsapp_delivery_log) and never
 * reach the caller.
 */
@Component
public class WhatsAppNotificationChannel implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationChannel.class);
    private static final int MAX_PARAM_LENGTH = 900;

    private final WhatsAppProperties properties;
    private final CompanyWhatsAppSettingRepository settings;
    private final WhatsAppOptInRepository optIns;
    private final WhatsAppDeliveryLogRepository deliveryLog;
    private final WhatsAppCloudClient client;
    private final UserRepository users;
    private final DemoCompanyGuard demoGuard;
    private final ExecutorService ownExecutor;
    private Executor executor;

    public WhatsAppNotificationChannel(WhatsAppProperties properties, CompanyWhatsAppSettingRepository settings,
                                       WhatsAppOptInRepository optIns, WhatsAppDeliveryLogRepository deliveryLog,
                                       WhatsAppCloudClient client, UserRepository users, DemoCompanyGuard demoGuard) {
        this.properties = properties;
        this.settings = settings;
        this.optIns = optIns;
        this.deliveryLog = deliveryLog;
        this.client = client;
        this.users = users;
        this.demoGuard = demoGuard;
        this.ownExecutor = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000),
                runnable -> {
                    Thread thread = new Thread(runnable, "whatsapp-notify");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.DiscardOldestPolicy());
        this.executor = ownExecutor;
    }

    /** Tests only: run deliveries synchronously. */
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    /** Called by NotificationService for every stored notification; cheap no-op when not applicable. */
    public void onNotification(Long companyId, Long userId, String type, String title, String message, String priority) {
        if (!properties.isActive() || companyId == null || userId == null) return;
        Optional<WhatsAppEvent> event = WhatsAppEvent.fromNotification(type, priority);
        if (event.isEmpty()) return;
        Runnable task = () -> {
            try {
                deliver(companyId, userId, event.get(), title, message);
            } catch (RuntimeException ex) {
                log.warn("WhatsApp notification for user {} ({}) failed", userId, type, ex);
            }
        };
        Runnable submit = () -> {
            try {
                executor.execute(task);
            } catch (RuntimeException ex) {
                log.warn("Could not queue WhatsApp notification for user {}", userId, ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
        } else {
            submit.run();
        }
    }

    /** Synchronous core. Returns true when a message was sent. */
    public boolean deliver(Long companyId, Long userId, WhatsAppEvent event, String title, String message) {
        if (!properties.isActive()) return false;
        String template = properties.template(event);
        if (template == null) return false;
        CompanyWhatsAppSetting setting = settings.findByCompany_IdAndDeletedFalse(companyId).orElse(null);
        if (setting == null || !setting.isEnabled() || !setting.eventSet().contains(event)) return false;
        if (demoGuard != null && demoGuard.isDemo(companyId)) return false;
        WhatsAppOptIn optIn = optIns.findByUser_IdAndCompany_IdAndDeletedFalse(userId, companyId).orElse(null);
        if (optIn == null || !optIn.isOptedIn() || optIn.getPhoneE164() == null) return false;
        String name = users.findById(userId).map(User::getFullName).orElse("there");

        WhatsAppDeliveryLog entry = new WhatsAppDeliveryLog();
        entry.setCompanyId(companyId);
        entry.setUserId(userId);
        entry.setEventType(event.name());
        entry.setTemplateName(template);
        entry.setPhoneMasked(mask(optIn.getPhoneE164()));
        try {
            String id = client.sendTemplate(optIn.getPhoneE164(), template, properties.language(),
                    List.of(clip(name), clip(title), clip(message)));
            entry.setStatus("SENT");
            entry.setProviderMessageId(id);
            return true;
        } catch (RuntimeException ex) {
            entry.setStatus("FAILED");
            String error = ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
            entry.setErrorMessage(error.length() > 1000 ? error.substring(0, 1000) : error);
            log.warn("WhatsApp send failed for user {} event {}: {}", userId, event, ex.getClass().getSimpleName());
            return false;
        } finally {
            try {
                deliveryLog.save(entry);
            } catch (RuntimeException ex) {
                log.warn("Could not record WhatsApp delivery log", ex);
            }
        }
    }

    static String mask(String phone) {
        if (phone == null || phone.length() < 6) return "****";
        return phone.substring(0, 3) + "*".repeat(phone.length() - 5) + phone.substring(phone.length() - 2);
    }

    private static String clip(String value) {
        if (value == null || value.isBlank()) return "-";
        // Template parameters may not contain newlines/tabs or 4+ consecutive spaces.
        String flat = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {4,}", "   ").trim();
        return flat.length() > MAX_PARAM_LENGTH ? flat.substring(0, MAX_PARAM_LENGTH) : flat;
    }

    @Override
    public void destroy() {
        ownExecutor.shutdown();
    }
}
