package com.haodaone.privacy.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.privacy.dto.PrivacyDTO;
import com.haodaone.privacy.entity.CompanyPrivacySetting;
import com.haodaone.privacy.entity.LegalDocumentType;
import com.haodaone.privacy.entity.MonitoringConsent;
import com.haodaone.privacy.repository.MonitoringConsentRepository;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Employee consent to the IT monitoring agent.
 *
 * Policy (documented for customers):
 *  - When the company setting monitoringConsentRequired is ON, a device
 *    cannot be enrolled for an employee (admin enrollment, installer
 *    download, or agent enrollment) until that employee has accepted the
 *    current monitoring notice.
 *  - Withdrawal is always possible and is recorded; admins are notified.
 *    The employer (data fiduciary) decides the consequences. Monitoring is
 *    NOT silently stopped unless the company setting
 *    stopMonitoringOnWithdrawal is ON, in which case the employee's
 *    monitored devices are deactivated (paused) immediately.
 */
@Service
public class MonitoringConsentService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringConsentService.class);
    private static final Set<String> ADMIN_ROLES = Set.of("COMPANY_ADMIN", "HR_ADMIN", "IT_ADMINISTRATOR");

    private final MonitoringConsentRepository consentRepository;
    private final PrivacySettingsService settingsService;
    private final LegalDocumentService documentService;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final MonitoredDeviceRepository deviceRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public MonitoringConsentService(MonitoringConsentRepository consentRepository, PrivacySettingsService settingsService,
                                    LegalDocumentService documentService, EmployeeRepository employeeRepository,
                                    UserRepository userRepository, MonitoredDeviceRepository deviceRepository,
                                    AuditLogService auditLogService, NotificationService notificationService) {
        this.consentRepository = consentRepository;
        this.settingsService = settingsService;
        this.documentService = documentService;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    /** The plain-language notice shown before enrollment and on the Privacy page. */
    public PrivacyDTO.MonitoringNotice notice() {
        PrivacyDTO.LegalDocumentInfo doc = documentService.current(LegalDocumentType.MONITORING_NOTICE);
        return new PrivacyDTO.MonitoringNotice(doc.version(), doc.url(),
                "Your employer uses the Vettri Windows Agent on work computers for IT asset management, security, "
                        + "productivity reporting and remote IT support. Your employer decides how this information is used.",
                List.of(
                        "Applications in use and active window titles, with start and end times",
                        "Activity signals: active vs idle time (keyboard/mouse activity counts, not the keys you type)",
                        "Device and hardware inventory: computer model, serial number, processor, memory, disk, Windows version, BIOS, installed software",
                        "Device status: online/offline heartbeats, agent version, IP address",
                        "Remote support sessions started by IT (screen view/control and commands), which are logged"),
                List.of(
                        "Keystrokes or the content of what you type",
                        "Passwords, product keys or browser data",
                        "Your personal files or file contents",
                        "Webcam or microphone"),
                "You can withdraw this consent at any time from the Privacy page. Your employer will be notified and "
                        + "decides what happens next; withdrawal does not by itself uninstall the agent.");
    }

    /** True when enrollment may proceed for this employee (policy off, or a current consent exists). */
    @Transactional(readOnly = true)
    public boolean isConsentSatisfied(Long companyId, Long employeeId) {
        if (companyId == null || employeeId == null) {
            return true;
        }
        CompanyPrivacySetting settings = settingsService.settingsFor(companyId);
        if (!settings.isMonitoringConsentRequired()) {
            return true;
        }
        return activeConsent(companyId, employeeId).isPresent();
    }

    /** Enrollment hook: throws a human-readable error when the company requires consent and it is missing. */
    @Transactional(readOnly = true)
    public void requireConsent(Long companyId, Long employeeId) {
        if (!isConsentSatisfied(companyId, employeeId)) {
            throw new BadRequestException("This employee has not yet accepted the monitoring notice. Your organization "
                    + "requires consent before a device can be enrolled - ask the employee to open their enrollment "
                    + "link or the Privacy page and accept the notice first.");
        }
    }

    @Transactional(readOnly = true)
    public PrivacyDTO.MonitoringConsentStatus status(Long userId) {
        Optional<Employee> employee = employeeRepository.findByUser_IdAndDeletedFalse(userId);
        if (employee.isEmpty() || employee.get().getCompany() == null) {
            return new PrivacyDTO.MonitoringConsentStatus(false, false, false, false, notice(), null, List.of());
        }
        Long companyId = employee.get().getCompany().getId();
        CompanyPrivacySetting settings = settingsService.settingsFor(companyId);
        List<MonitoringConsent> history = consentRepository
                .findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByGrantedAtDesc(companyId, employee.get().getId());
        MonitoringConsent current = history.stream()
                .filter(c -> MonitoringConsent.GRANTED.equals(c.getStatus())).findFirst().orElse(null);
        return new PrivacyDTO.MonitoringConsentStatus(settings.isMonitoringConsentRequired(), current != null, true,
                settings.isStopMonitoringOnWithdrawal(), notice(), current == null ? null : toView(current),
                history.stream().map(MonitoringConsentService::toView).toList());
    }

    @Transactional
    public PrivacyDTO.MonitoringConsentStatus grant(Long userId, HttpServletRequest httpRequest) {
        Employee employee = requireEmployee(userId);
        Long companyId = employee.getCompany().getId();
        String version = documentService.currentVersion(LegalDocumentType.MONITORING_NOTICE);
        Optional<MonitoringConsent> existing = activeConsent(companyId, employee.getId());
        if (existing.isPresent() && version.equals(existing.get().getNoticeVersion())) {
            return status(userId);
        }
        existing.ifPresent(old -> {
            // Superseded by consent to the newer notice version.
            old.setStatus(MonitoringConsent.WITHDRAWN);
            old.setWithdrawnAt(LocalDateTime.now());
            old.setWithdrawalReason("Superseded by consent to notice version " + version);
            consentRepository.save(old);
        });
        MonitoringConsent consent = new MonitoringConsent();
        consent.setCompany(employee.getCompany());
        consent.setEmployee(employee);
        consent.setUser(userRepository.findById(userId).orElse(null));
        consent.setNoticeVersion(version);
        consent.setStatus(MonitoringConsent.GRANTED);
        consent.setGrantedAt(LocalDateTime.now());
        if (httpRequest != null) {
            consent.setIpAddress(LegalAcceptanceService.truncate(httpRequest.getRemoteAddr(), 64));
            consent.setUserAgent(LegalAcceptanceService.truncate(httpRequest.getHeader("User-Agent"), 500));
        }
        MonitoringConsent saved = consentRepository.save(consent);
        auditLogService.log("MonitoringConsent", saved.getId(), "GRANT",
                "Employee " + employee.getId() + " accepted monitoring notice version " + version);
        return status(userId);
    }

    @Transactional
    public PrivacyDTO.MonitoringConsentStatus withdraw(Long userId, String reason) {
        Employee employee = requireEmployee(userId);
        Long companyId = employee.getCompany().getId();
        MonitoringConsent consent = activeConsent(companyId, employee.getId())
                .orElseThrow(() -> new BadRequestException("You have no active monitoring consent to withdraw."));
        consent.setStatus(MonitoringConsent.WITHDRAWN);
        consent.setWithdrawnAt(LocalDateTime.now());
        consent.setWithdrawalReason(reason == null || reason.isBlank() ? null : LegalAcceptanceService.truncate(reason.trim(), 1000));
        consentRepository.save(consent);

        CompanyPrivacySetting settings = settingsService.settingsFor(companyId);
        int paused = 0;
        if (settings.isStopMonitoringOnWithdrawal()) {
            for (MonitoredDevice device : deviceRepository
                    .findAllByCompany_IdAndEmployee_IdAndActiveTrueAndDeletedFalseOrderByDeviceNameAsc(companyId, employee.getId())) {
                device.setActive(false);
                deviceRepository.save(device);
                paused++;
            }
        }
        auditLogService.log("MonitoringConsent", consent.getId(), "WITHDRAW",
                "Employee " + employee.getId() + " withdrew monitoring consent"
                        + (paused > 0 ? "; " + paused + " monitored device(s) paused per company policy" : ""));
        notifyAdmins(companyId, employee, consent, paused);
        return status(userId);
    }

    @Transactional(readOnly = true)
    public List<PrivacyDTO.ConsentView> companyConsents() {
        Long companyId = PrivacySettingsService.requiredTenant();
        return consentRepository.findAllByCompany_IdAndDeletedFalseOrderByGrantedAtDesc(companyId).stream()
                .map(MonitoringConsentService::toView).toList();
    }

    private void notifyAdmins(Long companyId, Employee employee, MonitoringConsent consent, int paused) {
        String title = "Monitoring consent withdrawn";
        String message = employee.getFullName() + " withdrew consent to device monitoring."
                + (paused > 0 ? " Their " + paused + " monitored device(s) were paused automatically."
                : " Monitoring has not been changed - review and decide the next step per your policy.");
        try {
            for (User user : userRepository.findAllByCompanyIdAndDeletedFalse(companyId)) {
                boolean admin = user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(role -> role != null && ADMIN_ROLES.contains(role.getName()));
                if (admin && user.isActive()) {
                    notificationService.notifyUser(companyId, user.getId(), "MONITORING_CONSENT_WITHDRAWN", title,
                            message, "MonitoringConsent", consent.getId(), "HIGH");
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Could not notify admins about consent withdrawal {}: {}", consent.getId(), ex.getMessage());
        }
    }

    private Optional<MonitoringConsent> activeConsent(Long companyId, Long employeeId) {
        return consentRepository.findFirstByCompany_IdAndEmployee_IdAndStatusAndDeletedFalseOrderByGrantedAtDesc(
                companyId, employeeId, MonitoringConsent.GRANTED);
    }

    private Employee requireEmployee(Long userId) {
        Employee employee = employeeRepository.findByUser_IdAndDeletedFalse(userId)
                .orElseThrow(() -> new BadRequestException("Monitoring consent applies to employee accounts only."));
        if (employee.getCompany() == null) {
            throw new BadRequestException("Your employee profile is not linked to an organization.");
        }
        return employee;
    }

    static PrivacyDTO.ConsentView toView(MonitoringConsent c) {
        Employee e = c.getEmployee();
        return new PrivacyDTO.ConsentView(c.getId(), e == null ? null : e.getId(), e == null ? null : e.getFullName(),
                c.getNoticeVersion(), c.getStatus(), c.getGrantedAt(), c.getWithdrawnAt(), c.getWithdrawalReason());
    }
}
