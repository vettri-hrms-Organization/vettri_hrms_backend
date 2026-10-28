package com.haodaone.privacy.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.privacy.dto.PrivacyDTO;
import com.haodaone.privacy.entity.CompanyPrivacySetting;
import com.haodaone.privacy.entity.DataPrincipalRequest;
import com.haodaone.privacy.repository.DataPrincipalRequestRepository;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Data principal rights workflow (DPDP Act 2023): employees raise ACCESS,
 * CORRECTION or ERASURE requests; HR works them from a queue with a 30-day
 * due date. Every state change is audited.
 *
 *  - ACCESS: ready immediately; the ZIP is generated on download (nothing is
 *    stored) and the link expires after 7 days.
 *  - CORRECTION: free text, HR updates the record and completes the request.
 *  - ERASURE: rejected straight away (with the reason) while the employee is
 *    active or inside the retention period; otherwise HR runs the
 *    anonymisation from the queue.
 */
@Service
public class DataPrincipalRequestService {

    private static final Logger log = LoggerFactory.getLogger(DataPrincipalRequestService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final int EXPORT_VALID_DAYS = 7;
    private static final Set<String> HR_ROLES = Set.of("COMPANY_ADMIN", "HR_ADMIN");

    private final DataPrincipalRequestRepository requestRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final PrivacySettingsService settingsService;
    private final PersonalDataExportService exportService;
    private final EmployeeAnonymisationService anonymisationService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public DataPrincipalRequestService(DataPrincipalRequestRepository requestRepository,
                                       EmployeeRepository employeeRepository, UserRepository userRepository,
                                       PrivacySettingsService settingsService, PersonalDataExportService exportService,
                                       EmployeeAnonymisationService anonymisationService,
                                       AuditLogService auditLogService, NotificationService notificationService) {
        this.requestRepository = requestRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.exportService = exportService;
        this.anonymisationService = anonymisationService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    // ---- Employee self-service ----

    @Transactional
    public PrivacyDTO.DataRequestView create(Long userId, PrivacyDTO.DataRequestCreate body) {
        Employee employee = requireEmployee(userId);
        String type = body.type() == null ? "" : body.type().trim().toUpperCase(Locale.ROOT);
        if (!Set.of(DataPrincipalRequest.TYPE_ACCESS, DataPrincipalRequest.TYPE_CORRECTION,
                DataPrincipalRequest.TYPE_ERASURE).contains(type)) {
            throw new BadRequestException("Choose a valid request type: ACCESS, CORRECTION or ERASURE.");
        }
        String details = body.details() == null ? null : body.details().trim();
        if (DataPrincipalRequest.TYPE_CORRECTION.equals(type) && (details == null || details.isBlank())) {
            throw new BadRequestException("Describe what is incorrect and what it should say.");
        }

        LocalDateTime now = LocalDateTime.now();
        DataPrincipalRequest request = new DataPrincipalRequest();
        request.setCompany(employee.getCompany());
        request.setEmployee(employee);
        request.setRequestedByUserId(userId);
        request.setRequestType(type);
        request.setDetails(details == null || details.isBlank() ? null : details);
        request.setDueDate(LocalDate.now(IST).plusDays(DataPrincipalRequest.SLA_DAYS));

        String auditNote;
        switch (type) {
            case DataPrincipalRequest.TYPE_ACCESS -> {
                request.setStatus(DataPrincipalRequest.STATUS_READY);
                request.setReadyAt(now);
                request.setExpiresAt(now.plusDays(EXPORT_VALID_DAYS));
                auditNote = "Personal data export prepared";
            }
            case DataPrincipalRequest.TYPE_ERASURE -> {
                CompanyPrivacySetting settings = settingsService.settingsFor(employee.getCompany().getId());
                PrivacyDTO.ErasureEligibility eligibility =
                        EmployeeAnonymisationService.eligibility(employee, settings, LocalDate.now(IST));
                if (!eligibility.eligible()) {
                    request.setStatus(DataPrincipalRequest.STATUS_REJECTED);
                    request.setResolutionNote(eligibility.reason());
                    request.setResolvedAt(now);
                    auditNote = "Erasure request rejected automatically: " + eligibility.reason();
                } else {
                    request.setStatus(DataPrincipalRequest.STATUS_SUBMITTED);
                    auditNote = "Erasure request submitted for HR review";
                }
            }
            default -> {
                request.setStatus(DataPrincipalRequest.STATUS_SUBMITTED);
                auditNote = "Correction request submitted";
            }
        }
        DataPrincipalRequest saved = requestRepository.save(request);
        auditLogService.log("DataPrincipalRequest", saved.getId(), "CREATE",
                type + " request by employee " + employee.getId() + ": " + auditNote);
        if (DataPrincipalRequest.STATUS_SUBMITTED.equals(saved.getStatus())) {
            notifyHr(saved, "New privacy request",
                    employee.getFullName() + " raised a " + type.toLowerCase(Locale.ROOT)
                            + " request. Respond by " + saved.getDueDate() + ".");
        }
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public List<PrivacyDTO.DataRequestView> myRequests(Long userId) {
        Employee employee = employeeRepository.findByUser_IdAndDeletedFalse(userId).orElse(null);
        if (employee == null || employee.getCompany() == null) {
            return List.of();
        }
        return requestRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByCreatedAtDesc(
                employee.getCompany().getId(), employee.getId()).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public PrivacyDTO.ErasureEligibility myErasureEligibility(Long userId) {
        Employee employee = requireEmployee(userId);
        return EmployeeAnonymisationService.eligibility(employee,
                settingsService.settingsFor(employee.getCompany().getId()), LocalDate.now(IST));
    }

    /** Builds the ZIP for the requester's own READY access request. */
    @Transactional
    public byte[] download(Long userId, Long requestId) {
        Employee employee = requireEmployee(userId);
        DataPrincipalRequest request = requestRepository
                .findByIdAndCompany_IdAndDeletedFalse(requestId, employee.getCompany().getId())
                .filter(r -> r.getEmployee() != null && employee.getId().equals(r.getEmployee().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        if (!DataPrincipalRequest.TYPE_ACCESS.equals(request.getRequestType())
                || !DataPrincipalRequest.STATUS_READY.equals(request.getStatus())) {
            throw new BadRequestException("This request has no download available.");
        }
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            request.setStatus(DataPrincipalRequest.STATUS_COMPLETED);
            request.setResolutionNote("Download link expired. Raise a new request for a fresh copy.");
            request.setResolvedAt(LocalDateTime.now());
            requestRepository.save(request);
            throw new BadRequestException("This download has expired. Raise a new request for a fresh copy.");
        }
        byte[] zip = exportService.buildZip(employee);
        request.setDownloadCount(request.getDownloadCount() + 1);
        request.setLastDownloadedAt(LocalDateTime.now());
        requestRepository.save(request);
        auditLogService.log("DataPrincipalRequest", request.getId(), "DOWNLOAD",
                "Employee " + employee.getId() + " downloaded their personal data export");
        return zip;
    }

    // ---- HR queue ----

    @Transactional(readOnly = true)
    public List<PrivacyDTO.DataRequestView> companyQueue() {
        Long companyId = PrivacySettingsService.requiredTenant();
        return requestRepository.findAllByCompany_IdAndDeletedFalseOrderByDueDateAscIdAsc(companyId).stream()
                .map(this::toView).toList();
    }

    @Transactional
    public PrivacyDTO.DataRequestView update(Long requestId, Long actorUserId, PrivacyDTO.DataRequestUpdate body) {
        DataPrincipalRequest request = findInTenant(requestId);
        String status = body.status() == null ? null : body.status().trim().toUpperCase(Locale.ROOT);
        if (status == null || !Set.of(DataPrincipalRequest.STATUS_IN_PROGRESS, DataPrincipalRequest.STATUS_COMPLETED,
                DataPrincipalRequest.STATUS_REJECTED).contains(status)) {
            throw new BadRequestException("Status must be IN_PROGRESS, COMPLETED or REJECTED.");
        }
        if (isClosed(request)) {
            throw new BadRequestException("This request is already closed.");
        }
        if (DataPrincipalRequest.TYPE_ERASURE.equals(request.getRequestType())
                && DataPrincipalRequest.STATUS_COMPLETED.equals(status)) {
            throw new BadRequestException("Use \"Anonymise employee\" to complete an erasure request.");
        }
        if (DataPrincipalRequest.STATUS_REJECTED.equals(status)
                && (body.resolutionNote() == null || body.resolutionNote().isBlank())) {
            throw new BadRequestException("Explain why the request is rejected - the employee will see this.");
        }
        request.setStatus(status);
        if (body.resolutionNote() != null && !body.resolutionNote().isBlank()) {
            request.setResolutionNote(body.resolutionNote().trim());
        }
        if (!DataPrincipalRequest.STATUS_IN_PROGRESS.equals(status)) {
            request.setResolvedAt(LocalDateTime.now());
            request.setResolvedByUserId(actorUserId);
        }
        DataPrincipalRequest saved = requestRepository.save(request);
        auditLogService.log("DataPrincipalRequest", saved.getId(), "STATUS_" + status,
                "Privacy request moved to " + status + " by user " + actorUserId);
        notifyEmployee(saved, "Your privacy request was updated",
                "Your " + saved.getRequestType().toLowerCase(Locale.ROOT) + " request is now "
                        + status.replace('_', ' ').toLowerCase(Locale.ROOT) + ".");
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public PrivacyDTO.ErasureEligibility erasureEligibility(Long requestId) {
        DataPrincipalRequest request = findInTenant(requestId);
        return EmployeeAnonymisationService.eligibility(request.getEmployee(),
                settingsService.settingsFor(request.getCompany().getId()), LocalDate.now(IST));
    }

    /** HR action on an ERASURE request: re-checks eligibility, anonymises, completes the request. */
    @Transactional
    public PrivacyDTO.ErasureResult erase(Long requestId, Long actorUserId) {
        DataPrincipalRequest request = findInTenant(requestId);
        if (!DataPrincipalRequest.TYPE_ERASURE.equals(request.getRequestType())) {
            throw new BadRequestException("Only erasure requests can anonymise an employee.");
        }
        if (isClosed(request)) {
            throw new BadRequestException("This request is already closed.");
        }
        Employee employee = request.getEmployee();
        CompanyPrivacySetting settings = settingsService.settingsFor(request.getCompany().getId());
        PrivacyDTO.ErasureEligibility eligibility =
                EmployeeAnonymisationService.eligibility(employee, settings, LocalDate.now(IST));
        if (!eligibility.eligible()) {
            throw new BadRequestException(eligibility.reason());
        }
        // Notify before the login account is anonymised and disabled.
        notifyEmployee(request, "Your personal data has been erased",
                "Your erasure request was completed. Payroll and statutory records are retained as required by law.");
        PrivacyDTO.ErasureResult result = anonymisationService.anonymise(employee, settings, request.getId());
        request.setStatus(DataPrincipalRequest.STATUS_COMPLETED);
        request.setResolvedAt(LocalDateTime.now());
        request.setResolvedByUserId(actorUserId);
        request.setResolutionNote("Personal data anonymised. Erased: " + String.join(", ", result.anonymisedFields())
                + ". Retained: " + String.join("; ", result.retained()));
        request.setDetails(null);
        requestRepository.save(request);
        auditLogService.log("DataPrincipalRequest", request.getId(), "ERASED",
                "Erasure completed by user " + actorUserId + " for employee " + employee.getId());
        return result;
    }

    // ---- helpers ----

    private DataPrincipalRequest findInTenant(Long requestId) {
        Long companyId = PrivacySettingsService.requiredTenant();
        return requestRepository.findByIdAndCompany_IdAndDeletedFalse(requestId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Privacy request not found"));
    }

    private static boolean isClosed(DataPrincipalRequest request) {
        return DataPrincipalRequest.STATUS_COMPLETED.equals(request.getStatus())
                || DataPrincipalRequest.STATUS_REJECTED.equals(request.getStatus());
    }

    private Employee requireEmployee(Long userId) {
        Employee employee = employeeRepository.findByUser_IdAndDeletedFalse(userId)
                .orElseThrow(() -> new BadRequestException("Privacy requests are available to employee accounts. "
                        + "Your login is not linked to an employee profile."));
        if (employee.getCompany() == null) {
            throw new BadRequestException("Your employee profile is not linked to an organization.");
        }
        return employee;
    }

    private void notifyHr(DataPrincipalRequest request, String title, String message) {
        try {
            Long companyId = request.getCompany().getId();
            for (User user : userRepository.findAllByCompanyIdAndDeletedFalse(companyId)) {
                boolean hr = user.isActive() && user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(role -> role != null && HR_ROLES.contains(role.getName()));
                if (hr) {
                    notificationService.notifyUser(companyId, user.getId(), "PRIVACY_REQUEST", title, message,
                            "DataPrincipalRequest", request.getId(), "HIGH");
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Could not notify HR about privacy request {}: {}", request.getId(), ex.getMessage());
        }
    }

    private void notifyEmployee(DataPrincipalRequest request, String title, String message) {
        try {
            Employee employee = request.getEmployee();
            if (employee != null && employee.getUser() != null) {
                notificationService.notifyUser(request.getCompany().getId(), employee.getUser().getId(),
                        "PRIVACY_REQUEST_UPDATE", title, message, "DataPrincipalRequest", request.getId(), "NORMAL");
            }
        } catch (RuntimeException ex) {
            log.warn("Could not notify employee about privacy request {}: {}", request.getId(), ex.getMessage());
        }
    }

    PrivacyDTO.DataRequestView toView(DataPrincipalRequest r) {
        Employee e = r.getEmployee();
        boolean overdue = !isClosed(r) && !DataPrincipalRequest.STATUS_READY.equals(r.getStatus())
                && r.getDueDate() != null && r.getDueDate().isBefore(LocalDate.now(IST));
        return new PrivacyDTO.DataRequestView(r.getId(), e == null ? null : e.getId(),
                e == null ? null : e.getFullName(), e == null ? null : e.getEmployeeCode(), r.getRequestType(),
                r.getStatus(), r.getDetails(), r.getDueDate(), overdue, r.getResolutionNote(), r.getCreatedAt(),
                r.getResolvedAt(), r.getReadyAt(), r.getExpiresAt(), r.getDownloadCount());
    }
}
