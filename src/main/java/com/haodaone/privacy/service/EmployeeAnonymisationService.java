package com.haodaone.privacy.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.document.entity.EmployeeDocument;
import com.haodaone.document.repository.EmployeeDocumentRepository;
import com.haodaone.document.service.EmployeeDocumentS3StorageService;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.privacy.dto.PrivacyDTO;
import com.haodaone.privacy.entity.CompanyPrivacySetting;
import com.haodaone.privacy.entity.MonitoringConsent;
import com.haodaone.privacy.repository.MonitoringConsentRepository;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Erasure (DPDP Act 2023, s.12) for exited employees, implemented as
 * irreversible anonymisation rather than row deletion: payroll, attendance
 * and audit rows reference the employee and some of them must legally be
 * kept, so the employee row stays as a pseudonymous anchor.
 *
 * Erased / replaced:
 *  - name -> "Erased Employee #<id>", email -> erased-<id>@erased.invalid
 *  - phone, date of birth, gender, address, emergency contact, photo,
 *    biometric PIN, bank account/IFSC/bank name, PAN
 *  - UAN/ESI numbers too, once the payroll retention period has also ended
 *  - login account: username/email/name replaced, password scrambled, disabled
 *  - uploaded documents: files deleted from S3, rows soft-deleted and their
 *    document numbers / file names cleared
 *  - attendance punch display name, leave reasons, monitored window titles
 *  - IP address / user agent on monitoring consent records
 *
 * Retained (and why):
 *  - payroll items / payslip amounts, PF/ESI/PT/TDS figures, employee code,
 *    joining/exit dates: statutory record keeping (Income-tax Act s.192/203
 *    and Form 16/24Q, EPF & MP Act, ESI Act, state shops & establishments
 *    and PT rules) - kept for the payroll retention period (default 7 years);
 *  - UAN/ESI numbers while that payroll retention period is still running,
 *    so statutory queries from EPFO/ESIC can be answered;
 *  - attendance timestamps and leave dates/days (needed to substantiate
 *    paid-days in retained payroll), now pseudonymous;
 *  - consent and legal acceptance records (evidence of lawful processing),
 *    without IP/user agent;
 *  - audit log entries (security/accountability record).
 */
@Service
public class EmployeeAnonymisationService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeAnonymisationService.class);
    public static final String ERASED_LABEL = "[erased]";

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final EmployeeDocumentRepository documentRepository;
    private final MonitoringConsentRepository consentRepository;
    private final AuditLogService auditLogService;

    @Autowired(required = false)
    private EmployeeDocumentS3StorageService documentStorage;

    @PersistenceContext
    private EntityManager entityManager;

    public EmployeeAnonymisationService(EmployeeRepository employeeRepository, UserRepository userRepository,
                                        EmployeeDocumentRepository documentRepository,
                                        MonitoringConsentRepository consentRepository,
                                        AuditLogService auditLogService) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.consentRepository = consentRepository;
        this.auditLogService = auditLogService;
    }

    public void setDocumentStorage(EmployeeDocumentS3StorageService documentStorage) {
        this.documentStorage = documentStorage;
    }

    public static boolean isExited(Employee employee) {
        String status = EmploymentStatus.normalize(employee.getStatus());
        return EmploymentStatus.RESIGNED.equals(status) || EmploymentStatus.TERMINATED.equals(status);
    }

    /** Whether the employee's personal data may be erased today, with a human-readable reason. */
    public static PrivacyDTO.ErasureEligibility eligibility(Employee employee, CompanyPrivacySetting settings, LocalDate today) {
        if (!isExited(employee)) {
            return new PrivacyDTO.ErasureEligibility(false,
                    "Erasure is not available while you are employed. Your employer needs your personal data to run "
                            + "payroll, attendance and statutory compliance for your employment (a legitimate use under "
                            + "the DPDP Act 2023). You can download a copy of your data or ask for a correction instead. "
                            + "Erasure becomes available after you exit and your employer's retention period ends.",
                    null);
        }
        if (employee.getLastWorkingDate() == null) {
            return new PrivacyDTO.ErasureEligibility(false,
                    "Your exit date has not been recorded yet. HR must record your last working day before erasure can be scheduled.",
                    null);
        }
        LocalDate eligibleFrom = employee.getLastWorkingDate().plusMonths(Math.max(0, settings.getOtherDataRetentionMonths()));
        if (today.isBefore(eligibleFrom)) {
            return new PrivacyDTO.ErasureEligibility(false,
                    "Your employer keeps exited employees' records for " + settings.getOtherDataRetentionMonths()
                            + " months after the last working day. Erasure will be possible from " + eligibleFrom + ".",
                    eligibleFrom);
        }
        return new PrivacyDTO.ErasureEligibility(true, "Eligible for erasure.", eligibleFrom);
    }

    /**
     * Irreversibly anonymises the employee. Callers must check eligibility first
     * (DataPrincipalRequestService does). Returns what was erased and what was kept.
     */
    @Transactional
    public PrivacyDTO.ErasureResult anonymise(Employee employee, CompanyPrivacySetting settings, Long requestId) {
        Long id = employee.getId();
        List<String> fields = new ArrayList<>();

        employee.setFirstName("Erased");
        employee.setLastName("Employee #" + id);
        employee.setEmail("erased-" + id + "@erased.invalid");
        fields.add("name");
        fields.add("email");
        employee.setPhone(null);
        fields.add("phone");
        employee.setDateOfBirth(null);
        employee.setGender(null);
        fields.add("dateOfBirth");
        fields.add("gender");
        employee.setAddress(null);
        fields.add("address");
        employee.setEmergencyContactName(null);
        employee.setEmergencyContactPhone(null);
        fields.add("emergencyContact");
        employee.setProfilePhotoUrl(null);
        fields.add("profilePhoto");
        employee.setBiometricDeviceUserId(null);
        fields.add("biometricPin");
        employee.setBankAccountNumber(null);
        employee.setBankIfsc(null);
        employee.setBankName(null);
        fields.add("bankAccount");
        employee.setPanNumber(null);
        fields.add("pan");
        boolean payrollRetentionOver = employee.getLastWorkingDate() != null
                && !LocalDate.now().isBefore(employee.getLastWorkingDate().plusYears(settings.getPayrollRetentionYears()));
        if (payrollRetentionOver) {
            employee.setUanNumber(null);
            employee.setEsiNumber(null);
            fields.add("uan");
            fields.add("esi");
        }
        employeeRepository.save(employee);

        User user = employee.getUser();
        if (user != null) {
            user.setUsername("erased-user-" + user.getId());
            user.setEmail("erased-user-" + user.getId() + "@erased.invalid");
            user.setFullName("Erased Employee #" + id);
            user.setPasswordHash("ERASED-" + UUID.randomUUID());
            user.setActive(false);
            userRepository.save(user);
            fields.add("loginAccount");
        }

        int documentsDeleted = 0;
        Long companyId = employee.getCompany() == null ? null : employee.getCompany().getId();
        for (EmployeeDocument doc : documentRepository.findAllByCompany_IdAndEmployeeIdAndDeletedFalseOrderByExpiryDateAsc(companyId, id)) {
            if (doc.getS3ObjectKey() != null && documentStorage != null) {
                try {
                    documentStorage.delete(doc.getS3ObjectKey());
                } catch (RuntimeException ex) {
                    log.warn("Could not delete document {} from storage during erasure: {}", doc.getId(), ex.getMessage());
                }
            }
            doc.setDocumentNumber(null);
            doc.setOriginalFileName(ERASED_LABEL);
            doc.setNotes(null);
            doc.setRejectionReason(null);
            doc.setDeleted(true);
            doc.setDeletedAt(LocalDateTime.now());
            documentRepository.save(doc);
            documentsDeleted++;
        }
        fields.add("documents");

        for (MonitoringConsent consent : consentRepository
                .findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByGrantedAtDesc(companyId, id)) {
            consent.setIpAddress(null);
            consent.setUserAgent(null);
            consentRepository.save(consent);
        }

        if (entityManager != null) {
            entityManager.createQuery("UPDATE AttendanceRecord a SET a.employeeName = :label WHERE a.employee = :employee")
                    .setParameter("label", "Erased Employee #" + id).setParameter("employee", employee).executeUpdate();
            entityManager.createQuery("UPDATE LeaveRequest l SET l.reason = NULL WHERE l.employee = :employee")
                    .setParameter("employee", employee).executeUpdate();
            entityManager.createQuery("UPDATE ActivitySession s SET s.windowTitle = NULL WHERE s.employee = :employee")
                    .setParameter("employee", employee).executeUpdate();
            fields.add("attendanceName");
            fields.add("leaveReasons");
            fields.add("windowTitles");
        }

        List<String> retained = new ArrayList<>(List.of(
                "Employee code, joining and exit dates (pseudonymous anchor for retained records)",
                "Payroll items and payslip amounts incl. PF/ESI/PT/TDS figures - statutory record keeping for "
                        + settings.getPayrollRetentionYears() + " years",
                "Attendance timestamps and leave dates (support retained payroll paid-days)",
                "Consent and legal acceptance records without IP/user agent (evidence of lawful processing)",
                "Audit log entries (security and accountability)"));
        if (!payrollRetentionOver) {
            retained.add("UAN and ESI numbers until the payroll retention period ends (statutory EPFO/ESIC queries)");
        }
        auditLogService.log("Employee", id, "ANONYMISE",
                "Personal data erased under privacy request " + requestId + "; erased: " + String.join(", ", fields)
                        + "; " + documentsDeleted + " document file(s) deleted");
        return new PrivacyDTO.ErasureResult(requestId, id, fields, documentsDeleted, retained);
    }
}
