package com.haodaone.notifications.whatsapp;

import com.haodaone.demo.service.DemoCompanyGuard;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** WhatsApp fan-out happens only when server + company + event + opt-in all allow it; failures never escape. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppNotificationChannelTest {

    private static final long COMPANY = 7L;
    private static final long USER = 42L;

    @Mock private CompanyWhatsAppSettingRepository settings;
    @Mock private WhatsAppOptInRepository optIns;
    @Mock private WhatsAppDeliveryLogRepository deliveryLog;
    @Mock private WhatsAppCloudClient client;
    @Mock private UserRepository users;
    @Mock private DemoCompanyGuard demoGuard;

    private CompanyWhatsAppSetting setting;
    private WhatsAppOptIn optIn;

    @BeforeEach
    void setUp() {
        setting = new CompanyWhatsAppSetting();
        setting.setEnabled(true);
        setting.setEventSet(EnumSet.of(WhatsAppEvent.LEAVE_DECISION, WhatsAppEvent.ANNOUNCEMENT_URGENT));
        optIn = new WhatsAppOptIn();
        optIn.setOptedIn(true);
        optIn.setPhoneE164("+919876543210");
        User user = new User();
        user.setFullName("Asha Iyer");
        when(settings.findByCompany_IdAndDeletedFalse(COMPANY)).thenReturn(Optional.of(setting));
        when(optIns.findByUser_IdAndCompany_IdAndDeletedFalse(USER, COMPANY)).thenReturn(Optional.of(optIn));
        when(users.findById(USER)).thenReturn(Optional.of(user));
        when(demoGuard.isDemo(COMPANY)).thenReturn(false);
        when(client.sendTemplate(anyString(), anyString(), anyString(), anyList())).thenReturn("wamid.1");
    }

    private WhatsAppNotificationChannel channel(boolean serverEnabled) {
        WhatsAppProperties properties = new WhatsAppProperties(serverEnabled, "https://graph.facebook.com/v21.0",
                "12345", "token", "en", "leave_update", "", "urgent_notice", "", "");
        WhatsAppNotificationChannel channel = new WhatsAppNotificationChannel(properties, settings, optIns, deliveryLog,
                client, users, demoGuard);
        channel.setExecutor(Runnable::run);
        return channel;
    }

    @Test
    void sendsTemplateWithParametersAndLogsWhenEverythingAllowsIt() {
        channel(true).onNotification(COMPANY, USER, "LEAVE_DECISION", "Leave request approved",
                "Your leave request\nwas approved.", "NORMAL");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> params = ArgumentCaptor.forClass(List.class);
        verify(client).sendTemplate(eq("+919876543210"), eq("leave_update"), eq("en"), params.capture());
        assertEquals(List.of("Asha Iyer", "Leave request approved", "Your leave request was approved."), params.getValue());
        ArgumentCaptor<WhatsAppDeliveryLog> logged = ArgumentCaptor.forClass(WhatsAppDeliveryLog.class);
        verify(deliveryLog).save(logged.capture());
        assertEquals("SENT", logged.getValue().getStatus());
        assertEquals("wamid.1", logged.getValue().getProviderMessageId());
        assertFalse(logged.getValue().getPhoneMasked().contains("98765"));
    }

    @Test
    void doesNothingWhenTheServerSwitchIsOff() {
        channel(false).onNotification(COMPANY, USER, "LEAVE_DECISION", "t", "m", "NORMAL");
        verify(client, never()).sendTemplate(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void doesNothingWithoutAnActiveOptIn() {
        optIn.setOptedIn(false);
        assertFalse(channel(true).deliver(COMPANY, USER, WhatsAppEvent.LEAVE_DECISION, "t", "m"));
        when(optIns.findByUser_IdAndCompany_IdAndDeletedFalse(USER, COMPANY)).thenReturn(Optional.empty());
        assertFalse(channel(true).deliver(COMPANY, USER, WhatsAppEvent.LEAVE_DECISION, "t", "m"));
        verify(client, never()).sendTemplate(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void respectsCompanySwitchChosenEventsTemplatesAndDemoCompanies() {
        WhatsAppNotificationChannel channel = channel(true);
        // Event not chosen by the company.
        channel.onNotification(COMPANY, USER, "EXPENSE_APPROVED", "t", "m", "NORMAL");
        // Unmapped notification type.
        channel.onNotification(COMPANY, USER, "DOCUMENT_UPLOADED", "t", "m", "NORMAL");
        // Announcements only go out when urgent.
        channel.onNotification(COMPANY, USER, "ANNOUNCEMENT", "t", "m", "NORMAL");
        verify(client, never()).sendTemplate(anyString(), anyString(), anyString(), anyList());

        channel.onNotification(COMPANY, USER, "ANNOUNCEMENT", "Announcement: Server maintenance", "m", "HIGH");
        verify(client).sendTemplate(eq("+919876543210"), eq("urgent_notice"), eq("en"), anyList());

        when(demoGuard.isDemo(COMPANY)).thenReturn(true);
        assertFalse(channel.deliver(COMPANY, USER, WhatsAppEvent.LEAVE_DECISION, "t", "m"));

        when(demoGuard.isDemo(COMPANY)).thenReturn(false);
        setting.setEnabled(false);
        assertFalse(channel.deliver(COMPANY, USER, WhatsAppEvent.LEAVE_DECISION, "t", "m"));
    }

    @Test
    void providerFailuresAreLoggedAndNeverThrown() {
        when(client.sendTemplate(anyString(), anyString(), anyString(), anyList()))
                .thenThrow(new IllegalStateException("HTTP 400 template not approved"));
        WhatsAppNotificationChannel channel = channel(true);
        channel.onNotification(COMPANY, USER, "LEAVE_DECISION", "t", "m", "NORMAL");
        ArgumentCaptor<WhatsAppDeliveryLog> logged = ArgumentCaptor.forClass(WhatsAppDeliveryLog.class);
        verify(deliveryLog).save(logged.capture());
        assertEquals("FAILED", logged.getValue().getStatus());
        assertTrue(logged.getValue().getErrorMessage().contains("template not approved"));
    }

    @Test
    void phoneNumbersMustBeE164() {
        assertEquals("+919876543210", WhatsAppService.normalizePhone("+91 98765 43210"));
        assertEquals("+919876543210", WhatsAppService.normalizePhone("0091-98765-43210"));
        assertEquals(null, WhatsAppService.normalizePhone("9876543210"));
        assertEquals(null, WhatsAppService.normalizePhone("+0123"));
        verify(client, never()).sendTemplate(anyString(), anyString(), anyString(), any());
    }
}
