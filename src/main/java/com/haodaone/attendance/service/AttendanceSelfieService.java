package com.haodaone.attendance.service;

import com.haodaone.attendance.entity.AttendanceSession;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.security.AuthorizationService;
import com.haodaone.security.EmployeeSecurity;
import com.haodaone.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Check-in selfie rules: policy enforcement (SELFIE_REQUIRED), validation, storage,
 * authorised viewing and retention purge. No face recognition is performed - the
 * image is only kept as evidence for HR review and deleted after the retention window.
 */
@Service
public class AttendanceSelfieService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceSelfieService.class);
    public static final int DEFAULT_RETENTION_DAYS = 90;

    private final AttendanceSelfieStorageService storage;
    private final AttendanceSessionRepository sessionRepository;
    private final AuthorizationService authorizationService;
    private final EmployeeSecurity employeeSecurity;
    private final AuditLogService auditLogService;

    public AttendanceSelfieService(AttendanceSelfieStorageService storage,
                                   AttendanceSessionRepository sessionRepository,
                                   AuthorizationService authorizationService,
                                   EmployeeSecurity employeeSecurity,
                                   AuditLogService auditLogService) {
        this.storage = storage;
        this.sessionRepository = sessionRepository;
        this.authorizationService = authorizationService;
        this.employeeSecurity = employeeSecurity;
        this.auditLogService = auditLogService;
    }

    /**
     * Validates the selfie sent with a check-in. Returns null when none was sent and the
     * policy does not require one; throws BadRequestException("SELFIE_REQUIRED") when the
     * policy requires one and it is missing; throws SELFIE_TOO_LARGE / SELFIE_INVALID_TYPE
     * for bad images.
     */
    public SelfieImage validateForCheckIn(Company company, String encodedSelfie) {
        boolean missing = encodedSelfie == null || encodedSelfie.isBlank();
        if (missing) {
            if (company != null && company.isRequireSelfieAtCheckIn()) {
                throw new BadRequestException("SELFIE_REQUIRED");
            }
            return null;
        }
        return SelfieImage.decode(encodedSelfie);
    }

    /** Stores a validated selfie and links it to the (not yet saved) session. */
    public void attach(AttendanceSession session, SelfieImage image) {
        if (image == null) return;
        String key = storage.store(image, session.getCompany().getId(), session.getEmployee().getId(),
                session.getAttendanceDate());
        session.setCheckInSelfieKey(key);
    }

    /** Loads a selfie for the current viewer: ATTENDANCE_VIEW over that employee (scoped) or the employee themself. */
    @Transactional
    public SelfieContent loadForViewer(Long sessionId) {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required.");
        AttendanceSession session = sessionRepository.findByIdAndCompany_Id(sessionId, companyId)
                .filter(row -> !row.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Attendance session not found."));
        Employee employee = session.getEmployee();
        Long employeeId = employee == null ? null : employee.getId();
        boolean allowed = employeeId != null
                && (authorizationService.isAllowed("ATTENDANCE_VIEW", "EMPLOYEE", employeeId)
                || employeeSecurity.isSelf(employeeId));
        if (!allowed) throw new AccessDeniedException("Not allowed to view this selfie.");
        String key = session.getCheckInSelfieKey();
        if (key == null || key.isBlank()) {
            throw new ResourceNotFoundException("No selfie is stored for this check-in.");
        }
        if (!employeeSecurity.isSelf(employeeId)) {
            auditLogService.log("AttendanceSelfie", session.getId(), "VIEW",
                    "Viewed check-in selfie of employee " + employeeId + " for " + session.getAttendanceDate());
        }
        return new SelfieContent(storage.read(key), AttendanceSelfieStorageService.contentTypeForKey(key));
    }

    /** Sessions whose selfie is past the company's retention window (attendance date before today - retention). */
    @Transactional(readOnly = true)
    public List<AttendanceSession> selectExpired(Company company, LocalDate today) {
        return sessionRepository
                .findTop500ByCompany_IdAndCheckInSelfieKeyIsNotNullAndAttendanceDateBeforeOrderByAttendanceDateAsc(
                        company.getId(), retentionCutoff(company, today));
    }

    public static LocalDate retentionCutoff(Company company, LocalDate today) {
        int days = company.getSelfieRetentionDays() > 0 ? company.getSelfieRetentionDays() : DEFAULT_RETENTION_DAYS;
        return today.minusDays(days);
    }

    /** Deletes expired selfie objects for one company and clears the keys. Idempotent. */
    @Transactional
    public int purgeExpired(Company company, LocalDate today) {
        int purged = 0;
        for (AttendanceSession session : selectExpired(company, today)) {
            try {
                storage.delete(session.getCheckInSelfieKey());
                session.setCheckInSelfieKey(null);
                sessionRepository.save(session);
                purged++;
            } catch (RuntimeException ex) {
                log.warn("Could not purge selfie for attendance session {}: {}", session.getId(), ex.getMessage());
            }
        }
        if (purged > 0) {
            log.info("Purged {} check-in selfie(s) for company {}", purged, company.getId());
        }
        return purged;
    }

    public record SelfieContent(byte[] bytes, String contentType) {}
}
