package com.haodaone.privacy.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.privacy.entity.CompanyPrivacySetting;
import com.haodaone.privacy.entity.MonitoringConsent;
import com.haodaone.privacy.repository.LegalDocumentRepository;
import com.haodaone.privacy.repository.MonitoringConsentRepository;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MonitoringConsentServiceTest {

    private final MonitoringConsentRepository consentRepository = mock(MonitoringConsentRepository.class);
    private final PrivacySettingsService settingsService = mock(PrivacySettingsService.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MonitoredDeviceRepository deviceRepository = mock(MonitoredDeviceRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private MonitoringConsentService service;
    private CompanyPrivacySetting settings;
    private Employee employee;
    private Company company;

    @BeforeEach
    void setUp() {
        service = new MonitoringConsentService(consentRepository, settingsService,
                new LegalDocumentService(mock(LegalDocumentRepository.class)), employeeRepository, userRepository,
                deviceRepository, auditLogService, notificationService);
        settings = new CompanyPrivacySetting();
        when(settingsService.settingsFor(3L)).thenReturn(settings);
        company = new Company();
        company.setId(3L);
        employee = new Employee();
        employee.setId(42L);
        employee.setFirstName("Priya");
        employee.setLastName("Raman");
        employee.setCompany(company);
        when(employeeRepository.findByUser_IdAndDeletedFalse(70L)).thenReturn(Optional.of(employee));
        when(consentRepository.findFirstByCompany_IdAndEmployee_IdAndStatusAndDeletedFalseOrderByGrantedAtDesc(
                3L, 42L, MonitoringConsent.GRANTED)).thenReturn(Optional.empty());
        when(consentRepository.save(any(MonitoringConsent.class))).thenAnswer(inv -> {
            MonitoringConsent c = inv.getArgument(0);
            if (c.getId() == null) c.setId(11L);
            return c;
        });
    }

    @Test
    void consentNotRequiredWhenCompanySettingOff() {
        settings.setMonitoringConsentRequired(false);
        assertTrue(service.isConsentSatisfied(3L, 42L));
        assertDoesNotThrow(() -> service.requireConsent(3L, 42L));
    }

    @Test
    void enrollmentBlockedWithoutConsentWhenSettingOn() {
        settings.setMonitoringConsentRequired(true);
        assertFalse(service.isConsentSatisfied(3L, 42L));
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.requireConsent(3L, 42L));
        assertTrue(ex.getMessage().contains("monitoring notice"));
    }

    @Test
    void enrollmentAllowedOnceEmployeeAccepted() {
        settings.setMonitoringConsentRequired(true);
        MonitoringConsent consent = new MonitoringConsent();
        consent.setEmployee(employee);
        consent.setStatus(MonitoringConsent.GRANTED);
        consent.setGrantedAt(LocalDateTime.now());
        when(consentRepository.findFirstByCompany_IdAndEmployee_IdAndStatusAndDeletedFalseOrderByGrantedAtDesc(
                3L, 42L, MonitoringConsent.GRANTED)).thenReturn(Optional.of(consent));
        assertDoesNotThrow(() -> service.requireConsent(3L, 42L));
    }

    @Test
    void grantRecordsCurrentNoticeVersion() {
        settings.setMonitoringConsentRequired(true);
        service.grant(70L, null);
        verify(consentRepository).save(argThat(c -> MonitoringConsent.GRANTED.equals(c.getStatus())
                && "2026-10-01".equals(c.getNoticeVersion()) && c.getEmployee() == employee));
    }

    @Test
    void withdrawalNotifiesAdminsAndOnlyPausesDevicesWhenPolicySaysSo() {
        MonitoringConsent consent = new MonitoringConsent();
        consent.setId(11L);
        consent.setEmployee(employee);
        consent.setStatus(MonitoringConsent.GRANTED);
        consent.setGrantedAt(LocalDateTime.now());
        when(consentRepository.findFirstByCompany_IdAndEmployee_IdAndStatusAndDeletedFalseOrderByGrantedAtDesc(
                3L, 42L, MonitoringConsent.GRANTED)).thenReturn(Optional.of(consent));
        Role adminRole = new Role();
        adminRole.setName("COMPANY_ADMIN");
        User admin = new User();
        admin.setId(1L);
        admin.setActive(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        when(userRepository.findAllByCompanyIdAndDeletedFalse(3L)).thenReturn(List.of(admin));
        MonitoredDevice device = new MonitoredDevice();
        device.setActive(true);
        when(deviceRepository.findAllByCompany_IdAndEmployee_IdAndActiveTrueAndDeletedFalseOrderByDeviceNameAsc(3L, 42L))
                .thenReturn(List.of(device));

        settings.setStopMonitoringOnWithdrawal(false);
        service.withdraw(70L, "Not comfortable");

        assertEquals(MonitoringConsent.WITHDRAWN, consent.getStatus());
        assertNotNull(consent.getWithdrawnAt());
        assertTrue(device.isActive(), "monitoring must not be silently stopped when the policy is off");
        verify(notificationService).notifyUser(eq(3L), eq(1L), eq("MONITORING_CONSENT_WITHDRAWN"), anyString(),
                anyString(), eq("MonitoringConsent"), eq(11L), eq("HIGH"));

        consent.setStatus(MonitoringConsent.GRANTED);
        settings.setStopMonitoringOnWithdrawal(true);
        service.withdraw(70L, null);
        assertFalse(device.isActive());
    }
}
