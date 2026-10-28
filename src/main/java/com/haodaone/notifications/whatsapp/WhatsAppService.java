package com.haodaone.notifications.whatsapp;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** WhatsApp opt-in (self-service) and the company channel settings (admin). */
@Service
public class WhatsAppService {

    /** E.164: + then country code and number, 8-15 digits total, no leading zero. */
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    public record OptInView(boolean optedIn, String phone, LocalDateTime optedInAt, boolean channelAvailable) {
    }

    public record OptInRequest(Boolean optIn, String phone) {
    }

    public record SettingsView(boolean serverEnabled, boolean enabled, List<String> events,
                               List<String> availableEvents, Map<String, Boolean> templateConfigured) {
    }

    public record SettingsRequest(Boolean enabled, List<String> events) {
    }

    public record DeliveryView(Long id, Long userId, String eventType, String templateName, String phone,
                               String status, String providerMessageId, String error, LocalDateTime createdAt) {
    }

    private final WhatsAppOptInRepository optIns;
    private final CompanyWhatsAppSettingRepository settings;
    private final WhatsAppDeliveryLogRepository deliveries;
    private final WhatsAppProperties properties;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final AuditLogService auditLogService;

    public WhatsAppService(WhatsAppOptInRepository optIns, CompanyWhatsAppSettingRepository settings,
                           WhatsAppDeliveryLogRepository deliveries, WhatsAppProperties properties,
                           UserRepository users, CompanyRepository companies, AuditLogService auditLogService) {
        this.optIns = optIns;
        this.settings = settings;
        this.deliveries = deliveries;
        this.properties = properties;
        this.users = users;
        this.companies = companies;
        this.auditLogService = auditLogService;
    }

    public static String normalizePhone(String raw) {
        if (raw == null) return null;
        String value = raw.trim().replaceAll("[\\s().-]", "");
        if (value.startsWith("00")) value = "+" + value.substring(2);
        return E164.matcher(value).matches() ? value : null;
    }

    @Transactional(readOnly = true)
    public OptInView optIn(Long userId, Long companyId) {
        boolean available = properties.isActive() && settings.findByCompany_IdAndDeletedFalse(companyId)
                .map(CompanyWhatsAppSetting::isEnabled).orElse(false);
        return optIns.findByUser_IdAndCompany_IdAndDeletedFalse(userId, companyId)
                .map(o -> new OptInView(o.isOptedIn(), o.getPhoneE164(), o.getOptedInAt(), available))
                .orElse(new OptInView(false, null, null, available));
    }

    @Transactional
    public OptInView updateOptIn(Long userId, Long companyId, OptInRequest request, String ipAddress) {
        if (request == null || request.optIn() == null) throw new BadRequestException("optIn is required");
        User user = users.findByIdAndCompanyIdAndDeletedFalse(userId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        WhatsAppOptIn optIn = optIns.findByUser_IdAndDeletedFalse(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (request.optIn()) {
            String phone = normalizePhone(request.phone());
            if (phone == null) {
                throw new BadRequestException("Enter your WhatsApp number with country code, e.g. +919876543210");
            }
            if (optIn == null) {
                optIn = new WhatsAppOptIn();
                optIn.setUser(user);
                optIn.setCompany(user.getCompany());
            }
            optIn.setPhoneE164(phone);
            optIn.setOptedIn(true);
            optIn.setOptedInAt(now);
            optIn.setOptedOutAt(null);
            optIn.setConsentIp(ipAddress);
            optIns.save(optIn);
            auditLogService.log("User", userId, "WHATSAPP_OPT_IN", "Opted in to WhatsApp notifications");
        } else if (optIn != null && optIn.isOptedIn()) {
            optIn.setOptedIn(false);
            optIn.setOptedOutAt(now);
            optIns.save(optIn);
            auditLogService.log("User", userId, "WHATSAPP_OPT_OUT", "Opted out of WhatsApp notifications");
        }
        return optIn(userId, companyId);
    }

    @Transactional(readOnly = true)
    public SettingsView settings(Long companyId) {
        CompanyWhatsAppSetting setting = settings.findByCompany_IdAndDeletedFalse(companyId).orElse(null);
        Map<String, Boolean> templates = new LinkedHashMap<>();
        for (WhatsAppEvent event : WhatsAppEvent.values()) templates.put(event.name(), properties.template(event) != null);
        List<String> events = setting == null ? List.of() : setting.eventSet().stream().map(Enum::name).toList();
        return new SettingsView(properties.isActive(), setting != null && setting.isEnabled(), events,
                Arrays.stream(WhatsAppEvent.values()).map(Enum::name).toList(), templates);
    }

    @Transactional
    public SettingsView updateSettings(Long companyId, SettingsRequest request) {
        CompanyWhatsAppSetting setting = settings.findByCompany_IdAndDeletedFalse(companyId).orElseGet(() -> {
            CompanyWhatsAppSetting created = new CompanyWhatsAppSetting();
            created.setCompany(companies.findById(companyId).orElseThrow(() -> new ResourceNotFoundException("Company not found")));
            return created;
        });
        if (request.enabled() != null) setting.setEnabled(request.enabled());
        if (request.events() != null) {
            Set<WhatsAppEvent> events = EnumSet.noneOf(WhatsAppEvent.class);
            for (String value : request.events()) {
                events.add(WhatsAppEvent.parse(value).orElseThrow(() -> new BadRequestException("Unknown event: " + value)));
            }
            setting.setEventSet(events);
        }
        settings.save(setting);
        auditLogService.log("CompanyWhatsAppSetting", setting.getId(), "UPDATE",
                "WhatsApp channel enabled=" + setting.isEnabled() + ", events=" + setting.getEvents());
        return settings(companyId);
    }

    @Transactional(readOnly = true)
    public List<DeliveryView> deliveries(Long companyId) {
        List<DeliveryView> result = new ArrayList<>();
        for (WhatsAppDeliveryLog log : deliveries.findTop100ByCompanyIdOrderByCreatedAtDescIdDesc(companyId)) {
            result.add(new DeliveryView(log.getId(), log.getUserId(), log.getEventType(), log.getTemplateName(),
                    log.getPhoneMasked(), log.getStatus(), log.getProviderMessageId(), log.getErrorMessage(), log.getCreatedAt()));
        }
        return result;
    }
}
