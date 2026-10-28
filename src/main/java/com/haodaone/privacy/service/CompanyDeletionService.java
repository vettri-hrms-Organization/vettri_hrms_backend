package com.haodaone.privacy.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.privacy.dto.PrivacyDTO;
import com.haodaone.privacy.entity.CompanyDeletionRequest;
import com.haodaone.privacy.entity.CompanyPrivacySetting;
import com.haodaone.privacy.repository.CompanyDeletionRequestRepository;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Company account deletion: a company admin requests deletion, the account
 * is soft-deleted after a 30-day cancellable grace period by a daily job.
 *
 * At the end of the grace period (job "processDueDeletions"):
 *  1. every employee is anonymised and their document files deleted
 *     (EmployeeAnonymisationService, retention checks bypassed - the
 *     customer, as data fiduciary, instructed deletion and was told to
 *     export first);
 *  2. every user of the company is anonymised and disabled;
 *  3. the company row is soft-deleted;
 *  4. only when app.privacy.hard-delete-enabled=true (default false):
 *     rows in every table with a company_id column are physically deleted
 *     (best effort, FK order resolved by retrying), except Vettri's own
 *     billing/tax records (invoices, payments, subscriptions - GST law) and
 *     legal acceptance / deletion request evidence.
 * Steps 1-3 make the data unusable and unidentifiable even with the flag off.
 */
@Service
public class CompanyDeletionService {

    private static final Logger log = LoggerFactory.getLogger(CompanyDeletionService.class);
    private static final Set<String> KEPT_TABLE_KEYWORDS = Set.of(
            "invoice", "payment", "subscription", "billing", "legal_acceptance", "company_deletion_request", "audit");

    private final CompanyDeletionRequestRepository deletionRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAnonymisationService anonymisationService;
    private final CompanyDataExportService exportService;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.privacy.hard-delete-enabled:false}")
    private boolean hardDeleteEnabled = false;

    public CompanyDeletionService(CompanyDeletionRequestRepository deletionRepository, CompanyRepository companyRepository,
                                  UserRepository userRepository, EmployeeRepository employeeRepository,
                                  EmployeeAnonymisationService anonymisationService,
                                  CompanyDataExportService exportService, AuditLogService auditLogService,
                                  JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.deletionRepository = deletionRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.anonymisationService = anonymisationService;
        this.exportService = exportService;
        this.auditLogService = auditLogService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public PrivacyDTO.DeletionRequestView current() {
        Long companyId = PrivacySettingsService.requiredTenant();
        return deletionRepository.findFirstByCompany_IdAndStatusAndDeletedFalseOrderByRequestedAtDesc(
                companyId, CompanyDeletionRequest.PENDING).map(CompanyDeletionService::toView).orElse(null);
    }

    @Transactional
    public PrivacyDTO.DeletionRequestView request(Long actorUserId, PrivacyDTO.DeletionRequestCreate body) {
        Long companyId = PrivacySettingsService.requiredTenant();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        if (body.confirmCompanyName() == null
                || !company.getName().trim().equalsIgnoreCase(body.confirmCompanyName().trim())) {
            throw new BadRequestException("The organization name you typed does not match.");
        }
        if (deletionRepository.findFirstByCompany_IdAndStatusAndDeletedFalseOrderByRequestedAtDesc(
                companyId, CompanyDeletionRequest.PENDING).isPresent()) {
            throw new BadRequestException("A deletion request is already scheduled for this organization.");
        }
        LocalDateTime now = LocalDateTime.now();
        CompanyDeletionRequest request = new CompanyDeletionRequest();
        request.setCompany(company);
        request.setRequestedByUserId(actorUserId);
        request.setRequestedAt(now);
        request.setScheduledFor(now.plusDays(CompanyDeletionRequest.GRACE_DAYS));
        request.setReason(body.reason() == null || body.reason().isBlank() ? null : body.reason().trim());
        CompanyDeletionRequest saved = deletionRepository.save(request);
        auditLogService.log("CompanyDeletionRequest", saved.getId(), "REQUEST",
                "Company account deletion requested by user " + actorUserId + ", scheduled for " + saved.getScheduledFor());
        return toView(saved);
    }

    @Transactional
    public PrivacyDTO.DeletionRequestView cancel(Long actorUserId) {
        Long companyId = PrivacySettingsService.requiredTenant();
        CompanyDeletionRequest request = deletionRepository
                .findFirstByCompany_IdAndStatusAndDeletedFalseOrderByRequestedAtDesc(companyId, CompanyDeletionRequest.PENDING)
                .orElseThrow(() -> new BadRequestException("There is no pending deletion request to cancel."));
        request.setStatus(CompanyDeletionRequest.CANCELLED);
        request.setCancelledAt(LocalDateTime.now());
        request.setCancelledByUserId(actorUserId);
        deletionRepository.save(request);
        auditLogService.log("CompanyDeletionRequest", request.getId(), "CANCEL",
                "Company account deletion cancelled by user " + actorUserId);
        return toView(request);
    }

    /** SUPER_ADMIN view across all companies. */
    @Transactional(readOnly = true)
    public List<PrivacyDTO.DeletionRequestView> all() {
        return deletionRepository.findAllByDeletedFalseOrderByRequestedAtDesc().stream()
                .map(CompanyDeletionService::toView).toList();
    }

    /** Daily at 02:30 IST: run deletions whose grace period has ended. Idempotent (only PENDING rows). */
    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Kolkata")
    public void processDueDeletions() {
        List<CompanyDeletionRequest> due = deletionRepository.findAllByStatusAndScheduledForBeforeAndDeletedFalse(
                CompanyDeletionRequest.PENDING, LocalDateTime.now());
        for (CompanyDeletionRequest request : due) {
            try {
                processOne(request.getId());
            } catch (RuntimeException ex) {
                log.error("Company deletion {} failed: {}", request.getId(), ex.getMessage(), ex);
            }
        }
    }

    void processOne(Long requestId) {
        Long companyId = transactionTemplate.execute(status -> {
            CompanyDeletionRequest request = deletionRepository.findById(requestId).orElse(null);
            if (request == null || !CompanyDeletionRequest.PENDING.equals(request.getStatus())) {
                return null;
            }
            Company company = request.getCompany();
            CompanyPrivacySetting erasureSettings = new CompanyPrivacySetting();
            erasureSettings.setOtherDataRetentionMonths(0);
            erasureSettings.setPayrollRetentionYears(0);
            int employees = 0;
            for (Employee employee : employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(company.getId())) {
                anonymisationService.anonymise(employee, erasureSettings, null);
                employee.setDeleted(true);
                employee.setDeletedAt(LocalDateTime.now());
                employeeRepository.save(employee);
                employees++;
            }
            int users = 0;
            for (User user : userRepository.findAllByCompanyIdAndDeletedFalse(company.getId())) {
                user.setUsername("deleted-user-" + user.getId());
                user.setEmail("deleted-user-" + user.getId() + "@deleted.invalid");
                user.setFullName("Deleted user");
                user.setPasswordHash("DELETED-" + UUID.randomUUID());
                user.setActive(false);
                user.setDeleted(true);
                user.setDeletedAt(LocalDateTime.now());
                userRepository.save(user);
                users++;
            }
            company.setDeleted(true);
            company.setDeletedAt(LocalDateTime.now());
            companyRepository.save(company);
            request.setStatus(CompanyDeletionRequest.COMPLETED);
            request.setCompletedAt(LocalDateTime.now());
            request.setCompletionNote("Anonymised " + employees + " employee(s) and " + users
                    + " user account(s); company soft-deleted."
                    + (hardDeleteEnabled ? " Physical purge started." : " Physical purge disabled (app.privacy.hard-delete-enabled=false)."));
            deletionRepository.save(request);
            auditLogService.log("CompanyDeletionRequest", request.getId(), "COMPLETE", request.getCompletionNote());
            return company.getId();
        });
        if (companyId != null && hardDeleteEnabled) {
            String purgeNote = purge(companyId);
            transactionTemplate.executeWithoutResult(status -> deletionRepository.findById(requestId).ifPresent(r -> {
                r.setCompletionNote(LegalAcceptanceService.truncate(r.getCompletionNote() + " " + purgeNote, 4000));
                deletionRepository.save(r);
            }));
        }
    }

    /** Best-effort physical delete; each statement auto-commits so one FK failure does not undo the rest. */
    private String purge(Long companyId) {
        List<String> remaining = new ArrayList<>();
        for (String table : exportService.companyTables()) {
            String lower = table.toLowerCase(Locale.ROOT);
            if (KEPT_TABLE_KEYWORDS.stream().noneMatch(lower::contains)) {
                remaining.add(table);
            }
        }
        int deletedRows = 0;
        for (int pass = 0; pass < 8 && !remaining.isEmpty(); pass++) {
            List<String> failed = new ArrayList<>();
            for (String table : remaining) {
                try {
                    deletedRows += jdbcTemplate.update("DELETE FROM " + table + " WHERE company_id = ?", companyId);
                } catch (DataAccessException ex) {
                    failed.add(table);
                }
            }
            if (failed.size() == remaining.size()) break;
            remaining = failed;
        }
        log.info("Company {} purge removed {} row(s); not purged: {}", companyId, deletedRows, remaining);
        return "Purged " + deletedRows + " row(s)." + (remaining.isEmpty() ? "" : " Not purged (referenced rows): " + remaining);
    }

    static PrivacyDTO.DeletionRequestView toView(CompanyDeletionRequest r) {
        Company c = r.getCompany();
        return new PrivacyDTO.DeletionRequestView(r.getId(), c == null ? null : c.getId(), c == null ? null : c.getName(),
                r.getStatus(), r.getRequestedAt(), r.getScheduledFor(), r.getReason(), r.getCancelledAt(),
                r.getCompletedAt(), r.getCompletionNote());
    }
}
