package com.haodaone.expense.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.expense.dto.ExpenseDtos.ClaimRequest;
import com.haodaone.expense.dto.ExpenseDtos.ClaimView;
import com.haodaone.expense.dto.ExpenseDtos.LineRequest;
import com.haodaone.expense.dto.ExpenseDtos.LineView;
import com.haodaone.expense.dto.ExpenseDtos.PolicyIssue;
import com.haodaone.expense.dto.ReimbursementPayable;
import com.haodaone.expense.entity.ExpenseCategory;
import com.haodaone.expense.entity.ExpenseClaim;
import com.haodaone.expense.entity.ExpenseClaimLine;
import com.haodaone.expense.entity.ExpenseSettings;
import com.haodaone.expense.repository.ExpenseCategoryRepository;
import com.haodaone.expense.repository.ExpenseClaimLineRepository;
import com.haodaone.expense.repository.ExpenseClaimRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExpenseClaimService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseClaimService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MAX_LINES = 50;
    private static final BigDecimal MAX_LINE_AMOUNT = new BigDecimal("10000000");
    private static final String ENTITY = "ExpenseClaim";

    private final ExpenseClaimRepository claimRepository;
    private final ExpenseClaimLineRepository lineRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseCategoryService categoryService;
    private final AttachmentStorageService storageService;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public ExpenseClaimService(ExpenseClaimRepository claimRepository,
                               ExpenseClaimLineRepository lineRepository,
                               ExpenseCategoryRepository categoryRepository,
                               ExpenseCategoryService categoryService,
                               AttachmentStorageService storageService,
                               AuthorizationService authorizationService,
                               AuditLogService auditLogService,
                               NotificationService notificationService) {
        this.claimRepository = claimRepository;
        this.lineRepository = lineRepository;
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
        this.storageService = storageService;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------ self service

    @Transactional(readOnly = true)
    public List<ClaimView> myClaims() {
        Employee me = requireSelf();
        return toViews(claimRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByIdDesc(requiredTenant(), me.getId()));
    }

    @Transactional
    public ClaimView create(ClaimRequest request) {
        Employee me = requireSelf();
        Long companyId = requiredTenant();
        categoryService.ensureDefaults(companyId);
        ExpenseClaim claim = new ExpenseClaim();
        claim.setCompany(me.getCompany());
        claim.setEmployee(me);
        applyHeader(claim, request);
        ExpenseClaim saved = claimRepository.save(claim);
        List<ExpenseClaimLine> lines = applyLines(saved, request.lines(), List.of(), companyId);
        saved.setTotalAmount(total(lines));
        claimRepository.save(saved);
        auditLogService.log(ENTITY, saved.getId(), "CREATE", "Created expense claim draft '" + saved.getTitle() + "'");
        return toView(saved, lines);
    }

    @Transactional
    public ClaimView update(Long id, ClaimRequest request) {
        ExpenseClaim claim = ownDraft(id);
        applyHeader(claim, request);
        List<ExpenseClaimLine> existing = lineRepository.findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(claim.getId());
        List<ExpenseClaimLine> lines = applyLines(claim, request.lines(), existing, requiredTenant());
        claim.setTotalAmount(total(lines));
        claimRepository.save(claim);
        auditLogService.log(ENTITY, claim.getId(), "UPDATE", "Updated expense claim draft '" + claim.getTitle() + "'");
        return toView(claim, lines);
    }

    @Transactional
    public void deleteDraft(Long id) {
        ExpenseClaim claim = ownDraft(id);
        LocalDateTime now = LocalDateTime.now(IST);
        for (ExpenseClaimLine line : lineRepository.findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(claim.getId())) {
            line.setDeleted(true);
            line.setDeletedAt(now);
            lineRepository.save(line);
        }
        claim.setDeleted(true);
        claim.setDeletedAt(now);
        claimRepository.save(claim);
        auditLogService.log(ENTITY, claim.getId(), "DELETE", "Deleted expense claim draft '" + claim.getTitle() + "'");
    }

    @Transactional
    public ClaimView uploadReceipt(Long claimId, Long lineId, MultipartFile file) {
        ExpenseClaim claim = ownDraft(claimId);
        ExpenseClaimLine line = lineOf(claim, lineId);
        AttachmentStorageService.StoredAttachment stored = storageService.store(file,
                "expense-receipts/" + claim.getCompany().getId() + "/" + claim.getEmployee().getId() + "/" + claim.getId());
        String previousKey = line.getReceiptS3Key();
        line.setReceiptS3Key(stored.key());
        line.setReceiptFileName(stored.originalName());
        line.setReceiptContentType(stored.contentType());
        line.setReceiptSizeBytes(stored.sizeBytes());
        lineRepository.save(line);
        if (previousKey != null) storageService.deleteQuietly(previousKey);
        auditLogService.log(ENTITY, claim.getId(), "RECEIPT_UPLOAD", "Uploaded receipt " + stored.originalName() + " for line " + line.getId());
        return toView(claim, lineRepository.findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(claim.getId()));
    }

    @Transactional
    public ClaimView removeReceipt(Long claimId, Long lineId) {
        ExpenseClaim claim = ownDraft(claimId);
        ExpenseClaimLine line = lineOf(claim, lineId);
        String previousKey = line.getReceiptS3Key();
        line.setReceiptS3Key(null);
        line.setReceiptFileName(null);
        line.setReceiptContentType(null);
        line.setReceiptSizeBytes(null);
        lineRepository.save(line);
        if (previousKey != null) storageService.deleteQuietly(previousKey);
        return toView(claim, lineRepository.findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(claim.getId()));
    }

    public record ReceiptDownload(InputStreamResource resource, String fileName, String contentType) {
    }

    @Transactional(readOnly = true)
    public ReceiptDownload receipt(Long claimId, Long lineId) {
        ExpenseClaim claim = loadClaim(claimId);
        if (!canView(claim, currentEmployee())) throw new AccessDeniedException("You can't view this expense claim.");
        ExpenseClaimLine line = lineOf(claim, lineId);
        if (!line.hasReceipt()) throw new BadRequestException("No receipt is attached to this line.");
        return new ReceiptDownload(storageService.retrieve(line.getReceiptS3Key()),
                line.getReceiptFileName() == null ? "receipt" : line.getReceiptFileName(),
                line.getReceiptContentType());
    }

    @Transactional
    public ClaimView submit(Long id) {
        ExpenseClaim claim = ownDraft(id);
        List<ExpenseClaimLine> lines = lineRepository.findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(claim.getId());
        if (lines.isEmpty()) throw new BadRequestException("Add at least one expense line before submitting.");
        for (ExpenseClaimLine line : lines) {
            if (!line.getCategory().isActive()) {
                throw new BadRequestException("Category '" + line.getCategory().getName()
                        + "' is no longer available. Pick another category for that line.");
            }
        }
        List<PolicyIssue> issues = policyIssues(lines);
        if (ExpensePolicyEvaluator.hasBlocking(issues)) {
            String blocking = issues.stream()
                    .filter(issue -> ExpensePolicyEvaluator.SEVERITY_BLOCK.equals(issue.severity()))
                    .map(PolicyIssue::message)
                    .collect(Collectors.joining(" "));
            throw new BadRequestException("This claim breaks expense policy: " + blocking);
        }
        String warnings = issues.stream().map(PolicyIssue::message).collect(Collectors.joining("\n"));
        claim.setPolicyWarnings(warnings.isBlank() ? null : truncate(warnings, 2000));
        claim.setTotalAmount(total(lines));
        claim.setStatus(ExpenseClaim.STATUS_SUBMITTED);
        claim.setSubmittedAt(LocalDateTime.now(IST));
        claimRepository.save(claim);
        auditLogService.log(ENTITY, claim.getId(), "SUBMIT", "Submitted expense claim '" + claim.getTitle() + "' for "
                + ExpensePolicyEvaluator.inr(claim.getTotalAmount())
                + (issues.isEmpty() ? "" : " with " + issues.size() + " policy warning(s)"));

        Employee employee = claim.getEmployee();
        Long companyId = claim.getCompany().getId();
        String title = "Expense claim awaiting approval";
        String message = employee.getFullName() + " submitted '" + claim.getTitle() + "' for "
                + ExpensePolicyEvaluator.inr(claim.getTotalAmount()) + ".";
        Employee manager = employee.getReportingManager();
        if (manager != null && manager.getUser() != null && !manager.isDeleted()) {
            notificationService.notifyUser(companyId, manager.getUser().getId(), "EXPENSE_SUBMITTED", title, message,
                    ENTITY, claim.getId(), "NORMAL");
        } else {
            notificationService.notifyPermissionRecipients(companyId, employee.getId(), "EXPENSE_MANAGE",
                    "EXPENSE_SUBMITTED", title, message, ENTITY, claim.getId());
        }
        return toView(claim, lines);
    }

    @Transactional(readOnly = true)
    public ClaimView get(Long id) {
        ExpenseClaim claim = loadClaim(id);
        if (!canView(claim, currentEmployee())) throw new AccessDeniedException("You can't view this expense claim.");
        if (ExpenseClaim.STATUS_DRAFT.equals(claim.getStatus()) && !isOwner(claim, currentEmployee())) {
            throw new ResourceNotFoundException("Expense claim not found.");
        }
        return toView(claim, lineRepository.findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(claim.getId()));
    }

    // ------------------------------------------------------------------ approvals

    /** SUBMITTED claims of people the caller may approve at the manager stage (never their own). */
    @Transactional(readOnly = true)
    public List<ClaimView> managerQueue() {
        Long companyId = requiredTenant();
        Employee me = currentEmployee();
        Optional<Set<Long>> scope = authorizationService.resolveEmployeeIds("EXPENSE_APPROVE");
        List<ExpenseClaim> claims = claimRepository.findAllByCompany_IdAndStatusInAndDeletedFalseOrderByIdAsc(
                companyId, List.of(ExpenseClaim.STATUS_SUBMITTED));
        List<ExpenseClaim> visible = claims.stream()
                .filter(claim -> !isOwner(claim, me))
                .filter(claim -> isReportingManager(claim, me)
                        || scope.isEmpty()
                        || scope.get().contains(claim.getEmployee().getId()))
                .toList();
        return toViews(visible);
    }

    /** Claims waiting on finance: manager-approved, approved-not-yet-paid, and submitted claims with no reporting manager. */
    @Transactional(readOnly = true)
    public List<ClaimView> financeQueue() {
        Long companyId = requiredTenant();
        List<ExpenseClaim> claims = claimRepository.findAllByCompany_IdAndStatusInAndDeletedFalseOrderByIdAsc(companyId,
                List.of(ExpenseClaim.STATUS_SUBMITTED, ExpenseClaim.STATUS_MANAGER_APPROVED, ExpenseClaim.STATUS_APPROVED));
        return toViews(claims.stream()
                .filter(claim -> !ExpenseClaim.STATUS_SUBMITTED.equals(claim.getStatus())
                        || !hasActiveManager(claim.getEmployee()))
                .toList());
    }

    @Transactional
    public ClaimView managerApprove(Long id, String note) {
        ExpenseClaim claim = loadClaim(id);
        Employee me = requireApprover(claim);
        if (!ExpenseClaim.STATUS_SUBMITTED.equals(claim.getStatus())) {
            throw new BadRequestException("Only submitted claims can be approved by a manager (current status: " + claim.getStatus() + ").");
        }
        if (!isManagerApprover(claim, me) && !authorizationService.hasOrganizationScope("EXPENSE_MANAGE")) {
            throw new AccessDeniedException("You're not an approver for this employee's expenses.");
        }
        claim.setStatus(ExpenseClaim.STATUS_MANAGER_APPROVED);
        claim.setManagerApprovedBy(me);
        claim.setManagerApprovedAt(LocalDateTime.now(IST));
        claim.setManagerNote(truncate(trimToNull(note), 500));
        claimRepository.save(claim);
        auditLogService.log(ENTITY, claim.getId(), "MANAGER_APPROVE", "Manager approved expense claim '" + claim.getTitle() + "'");
        Long companyId = claim.getCompany().getId();
        notifyEmployee(claim, "EXPENSE_MANAGER_APPROVED", "Expense claim approved by manager",
                "'" + claim.getTitle() + "' was approved by " + me.getFullName() + " and is now with finance for final approval.");
        notificationService.notifyPermissionRecipients(companyId, claim.getEmployee().getId(), "EXPENSE_MANAGE",
                "EXPENSE_AWAITING_FINANCE", "Expense claim awaiting final approval",
                claim.getEmployee().getFullName() + "'s claim '" + claim.getTitle() + "' ("
                        + ExpensePolicyEvaluator.inr(claim.getTotalAmount()) + ") is ready for final approval.",
                ENTITY, claim.getId());
        return toView(claim, lineRepository.findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(claim.getId()));
    }

    @Transactional
    public ClaimView finalApprove(Long id, String note) {
        ExpenseClaim claim = loadClaim(id);
        Employee me = requireApprover(claim);
        requireFinance();
        boolean managerStageDone = ExpenseClaim.STATUS_MANAGER_APPROVED.equals(claim.getStatus());
        boolean noManagerStage = ExpenseClaim.STATUS_SUBMITTED.equals(claim.getStatus()) && !hasActiveManager(claim.getEmployee());
        if (!managerStageDone && !noManagerStage) {
            throw new BadRequestException(ExpenseClaim.STATUS_SUBMITTED.equals(claim.getStatus())
                    ? "This claim is still waiting for the reporting manager's approval."
                    : "Only manager-approved claims can be given final approval (current status: " + claim.getStatus() + ").");
        }
        claim.setStatus(ExpenseClaim.STATUS_APPROVED);
        claim.setApprovedBy(me);
        claim.setApprovedAt(LocalDateTime.now(IST));
        claim.setApproverNote(truncate(trimToNull(note), 500));
        claimRepository.save(claim);
        auditLogService.log(ENTITY, claim.getId(), "APPROVE", "Final approval for expense claim '" + claim.getTitle() + "' ("
                + ExpensePolicyEvaluator.inr(claim.getTotalAmount()) + ")");
        boolean viaPayroll = ExpenseSettings.MODE_PAYROLL.equals(categoryService.reimbursementMode(claim.getCompany().getId()));
        notifyEmployee(claim, "EXPENSE_APPROVED", "Expense claim approved",
                "'" + claim.getTitle() + "' (" + ExpensePolicyEvaluator.inr(claim.getTotalAmount()) + ") was approved. "
                        + (viaPayroll ? "It will be reimbursed with your next salary." : "Finance will reimburse it separately."));
        return toView(claim, lineRepository.findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(claim.getId()));
    }

    @Transactional
    public ClaimView reject(Long id, String reason) {
        String cleanReason = trimToNull(reason);
        if (cleanReason == null) throw new BadRequestException("A rejection reason is required.");
        ExpenseClaim claim = loadClaim(id);
        Employee me = requireApprover(claim);
        String status = claim.getStatus();
        boolean finance = authorizationService.hasOrganizationScope("EXPENSE_MANAGE");
        if (ExpenseClaim.STATUS_SUBMITTED.equals(status)) {
            if (!finance && !isManagerApprover(claim, me)) {
                throw new AccessDeniedException("You're not an approver for this employee's expenses.");
            }
        } else if (ExpenseClaim.STATUS_MANAGER_APPROVED.equals(status) || ExpenseClaim.STATUS_APPROVED.equals(status)) {
            if (!finance) throw new AccessDeniedException("Only finance/HR can reject a claim at this stage.");
        } else {
            throw new BadRequestException("This claim can't be rejected (current status: " + status + ").");
        }
        claim.setRejectedStage(status);
        claim.setStatus(ExpenseClaim.STATUS_REJECTED);
        claim.setRejectedBy(me);
        claim.setRejectedAt(LocalDateTime.now(IST));
        claim.setRejectionReason(truncate(cleanReason, 500));
        claimRepository.save(claim);
        auditLogService.log(ENTITY, claim.getId(), "REJECT", "Rejected expense claim '" + claim.getTitle() + "' at " + status + ": " + cleanReason);
        notifyEmployee(claim, "EXPENSE_REJECTED", "Expense claim rejected",
                "'" + claim.getTitle() + "' was rejected by " + me.getFullName() + ". Reason: " + cleanReason);
        return toView(claim, lineRepository.findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(claim.getId()));
    }

    @Transactional
    public ClaimView markReimbursed(Long id, String paymentReference) {
        String reference = trimToNull(paymentReference);
        if (reference == null) throw new BadRequestException("Enter the payment reference (UTR / cheque / transaction id).");
        ExpenseClaim claim = loadClaim(id);
        requireApprover(claim);
        requireFinance();
        if (!ExpenseClaim.STATUS_APPROVED.equals(claim.getStatus())) {
            throw new BadRequestException("Only approved claims can be marked reimbursed (current status: " + claim.getStatus() + ").");
        }
        claim.setStatus(ExpenseClaim.STATUS_REIMBURSED);
        claim.setReimbursedAt(LocalDateTime.now(IST));
        claim.setReimbursementMethod(ExpenseClaim.METHOD_MANUAL);
        claim.setPaymentReference(truncate(reference, 120));
        claimRepository.save(claim);
        auditLogService.log(ENTITY, claim.getId(), "REIMBURSE", "Marked expense claim '" + claim.getTitle() + "' reimbursed, ref " + reference);
        notifyEmployee(claim, "EXPENSE_REIMBURSED", "Expense claim reimbursed",
                "'" + claim.getTitle() + "' (" + ExpensePolicyEvaluator.inr(claim.getTotalAmount()) + ") was reimbursed. Reference: " + reference);
        return toView(claim, lineRepository.findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(claim.getId()));
    }

    // ------------------------------------------------------------------ reports

    @Transactional(readOnly = true)
    public List<ClaimView> list(LocalDate from, LocalDate to, String status, Long departmentId) {
        return toViews(filtered(from, to, status, departmentId));
    }

    @Transactional(readOnly = true)
    public String exportCsv(LocalDate from, LocalDate to, String status, Long departmentId) {
        List<ExpenseClaim> claims = filtered(from, to, status, departmentId);
        Map<Long, List<ExpenseClaimLine>> linesByClaim = linesByClaim(claims.stream().map(ExpenseClaim::getId).toList());
        DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", "Claim ID", "Employee Code", "Employee", "Department", "Title", "Status",
                "Total (INR)", "Lines", "Categories", "Submitted", "Manager Approved", "Approved", "Reimbursed",
                "Method", "Payment Reference", "Rejection Reason", "Policy Warnings")).append('\n');
        for (ExpenseClaim claim : claims) {
            List<ExpenseClaimLine> lines = linesByClaim.getOrDefault(claim.getId(), List.of());
            String categories = lines.stream().map(line -> line.getCategory().getName()).distinct().collect(Collectors.joining("; "));
            Employee employee = claim.getEmployee();
            csv.append(String.join(",",
                    csvCell(String.valueOf(claim.getId())),
                    csvCell(employee.getEmployeeCode()),
                    csvCell(employee.getFullName()),
                    csvCell(employee.getDepartment() == null ? "" : employee.getDepartment().getName()),
                    csvCell(claim.getTitle()),
                    csvCell(claim.getStatus()),
                    csvCell(claim.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString()),
                    csvCell(String.valueOf(lines.size())),
                    csvCell(categories),
                    csvCell(claim.getSubmittedAt() == null ? "" : claim.getSubmittedAt().format(dateTime)),
                    csvCell(claim.getManagerApprovedAt() == null ? "" : claim.getManagerApprovedAt().format(date)),
                    csvCell(claim.getApprovedAt() == null ? "" : claim.getApprovedAt().format(date)),
                    csvCell(claim.getReimbursedAt() == null ? "" : claim.getReimbursedAt().format(date)),
                    csvCell(claim.getReimbursementMethod()),
                    csvCell(claim.getPaymentReference()),
                    csvCell(claim.getRejectionReason()),
                    csvCell(claim.getPolicyWarnings() == null ? "" : claim.getPolicyWarnings().replace('\n', ' '))))
                    .append('\n');
        }
        return csv.toString();
    }

    // ------------------------------------------------------------------ payroll hooks

    /**
     * Approved, unpaid claims for one employee that payroll should reimburse
     * in {@code period} (approved on or before the period's last day).
     * Returns an empty list when the company pays expenses separately.
     * Amounts are non-taxable reimbursements.
     */
    @Transactional(readOnly = true)
    public List<ReimbursementPayable> approvedUnreimbursed(Long companyId, Long employeeId, YearMonth period) {
        if (companyId == null || employeeId == null || period == null) return List.of();
        if (!ExpenseSettings.MODE_PAYROLL.equals(categoryService.reimbursementMode(companyId))) return List.of();
        LocalDateTime cutoff = period.atEndOfMonth().atTime(LocalTime.MAX);
        return claimRepository
                .findAllByCompany_IdAndEmployee_IdAndStatusAndReimbursedAtIsNullAndPayrollItemIdIsNullAndDeletedFalseOrderByIdAsc(
                        companyId, employeeId, ExpenseClaim.STATUS_APPROVED)
                .stream()
                .filter(claim -> claim.getApprovedAt() != null && !claim.getApprovedAt().isAfter(cutoff))
                .map(claim -> new ReimbursementPayable(claim.getId(), employeeId, claim.getTitle(),
                        claim.getTotalAmount(), claim.getApprovedAt(), false))
                .toList();
    }

    /**
     * Marks claims as reimbursed through payroll. Uses the current tenant.
     * Idempotent: claims already linked to the same payroll item are skipped;
     * claims no longer APPROVED (e.g. rejected meanwhile) are skipped with a warning.
     */
    @Transactional
    public void markPaid(List<Long> claimIds, Long payrollItemId) {
        if (claimIds == null || claimIds.isEmpty()) return;
        if (payrollItemId == null) throw new BadRequestException("Payroll item id is required.");
        Long companyId = requiredTenant();
        LocalDateTime now = LocalDateTime.now(IST);
        for (ExpenseClaim claim : claimRepository.findAllByCompany_IdAndIdInAndDeletedFalse(companyId, new HashSet<>(claimIds))) {
            if (payrollItemId.equals(claim.getPayrollItemId())) continue;
            if (!ExpenseClaim.STATUS_APPROVED.equals(claim.getStatus())) {
                log.warn("Skipping expense claim {} in payroll item {}: status is {}", claim.getId(), payrollItemId, claim.getStatus());
                continue;
            }
            claim.setStatus(ExpenseClaim.STATUS_REIMBURSED);
            claim.setReimbursedAt(now);
            claim.setReimbursementMethod(ExpenseClaim.METHOD_PAYROLL);
            claim.setPayrollItemId(payrollItemId);
            claimRepository.save(claim);
            auditLogService.log(ENTITY, claim.getId(), "REIMBURSE", "Reimbursed through payroll item " + payrollItemId);
            notifyEmployee(claim, "EXPENSE_REIMBURSED", "Expense claim reimbursed",
                    "'" + claim.getTitle() + "' (" + ExpensePolicyEvaluator.inr(claim.getTotalAmount()) + ") was paid with your salary.");
        }
    }

    // ------------------------------------------------------------------ internals

    private List<ExpenseClaim> filtered(LocalDate from, LocalDate to, String status, Long departmentId) {
        Long companyId = requiredTenant();
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        return claimRepository.findAllByCompany_IdAndDeletedFalseOrderByIdDesc(companyId).stream()
                .filter(claim -> normalizedStatus == null
                        ? !ExpenseClaim.STATUS_DRAFT.equals(claim.getStatus())
                        : normalizedStatus.equals(claim.getStatus()))
                .filter(claim -> departmentId == null || (claim.getEmployee().getDepartment() != null
                        && departmentId.equals(claim.getEmployee().getDepartment().getId())))
                .filter(claim -> {
                    LocalDateTime stamp = claim.getSubmittedAt() != null ? claim.getSubmittedAt() : claim.getCreatedAt();
                    LocalDate day = stamp == null ? null : stamp.toLocalDate();
                    if (day == null) return from == null && to == null;
                    return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
                })
                .toList();
    }

    private void applyHeader(ExpenseClaim claim, ClaimRequest request) {
        if (request == null) throw new BadRequestException("Claim details are required.");
        String title = trimToNull(request.title());
        if (title == null) throw new BadRequestException("Claim title is required.");
        if (title.length() > 200) throw new BadRequestException("Claim title must be 200 characters or fewer.");
        String purpose = trimToNull(request.purpose());
        if (purpose != null && purpose.length() > 1000) throw new BadRequestException("Purpose must be 1000 characters or fewer.");
        claim.setTitle(title);
        claim.setPurpose(purpose);
    }

    List<ExpenseClaimLine> applyLines(ExpenseClaim claim, List<LineRequest> requests, List<ExpenseClaimLine> existing,
                                      Long companyId) {
        List<LineRequest> safeRequests = requests == null ? List.of() : requests;
        if (safeRequests.size() > MAX_LINES) throw new BadRequestException("A claim can have at most " + MAX_LINES + " lines.");
        Map<Long, ExpenseClaimLine> existingById = new HashMap<>();
        existing.forEach(line -> existingById.put(line.getId(), line));
        Map<Long, ExpenseCategory> categoryCache = new HashMap<>();
        Set<Long> kept = new HashSet<>();
        List<ExpenseClaimLine> result = new ArrayList<>();
        LocalDate today = LocalDate.now(IST);
        int number = 0;
        for (LineRequest request : safeRequests) {
            number++;
            String prefix = "Line " + number + ": ";
            if (request == null) throw new BadRequestException(prefix + "details are missing.");
            ExpenseClaimLine line = null;
            if (request.id() != null) {
                line = existingById.get(request.id());
                if (line == null) throw new BadRequestException(prefix + "this line no longer exists on the claim.");
                kept.add(line.getId());
            }
            if (line == null) {
                line = new ExpenseClaimLine();
                line.setClaim(claim);
                line.setCompany(claim.getCompany());
            }
            if (request.expenseDate() == null) throw new BadRequestException(prefix + "expense date is required.");
            if (request.expenseDate().isAfter(today)) throw new BadRequestException(prefix + "expense date can't be in the future.");
            if (request.categoryId() == null) throw new BadRequestException(prefix + "category is required.");
            ExpenseCategory category = categoryCache.computeIfAbsent(request.categoryId(),
                    categoryId -> categoryRepository.findByIdAndCompany_IdAndDeletedFalse(categoryId, companyId)
                            .orElseThrow(() -> new BadRequestException("Unknown expense category.")));
            boolean unchangedCategory = line.getCategory() != null && Objects.equals(line.getCategory().getId(), category.getId());
            if (!category.isActive() && !unchangedCategory) {
                throw new BadRequestException(prefix + "category '" + category.getName() + "' is not active.");
            }
            BigDecimal amount;
            if (request.mileageKm() != null) {
                if (request.mileageKm().signum() <= 0) throw new BadRequestException(prefix + "distance must be greater than zero.");
                BigDecimal rate = category.getMileageRatePerKm() != null ? category.getMileageRatePerKm() : request.mileageRate();
                if (rate == null || rate.signum() <= 0) {
                    throw new BadRequestException(prefix + "enter a rate per km for the mileage claim.");
                }
                BigDecimal km = request.mileageKm().setScale(2, RoundingMode.HALF_UP);
                BigDecimal scaledRate = rate.setScale(2, RoundingMode.HALF_UP);
                amount = mileageAmount(km, scaledRate);
                line.setMileageKm(km);
                line.setMileageRate(scaledRate);
            } else {
                if (request.amount() == null || request.amount().signum() <= 0) {
                    throw new BadRequestException(prefix + "amount must be greater than zero.");
                }
                amount = request.amount().setScale(2, RoundingMode.HALF_UP);
                line.setMileageKm(null);
                line.setMileageRate(null);
            }
            if (amount.compareTo(MAX_LINE_AMOUNT) >= 0) throw new BadRequestException(prefix + "amount is too large.");
            String description = trimToNull(request.description());
            if (description != null && description.length() > 500) {
                throw new BadRequestException(prefix + "description must be 500 characters or fewer.");
            }
            line.setExpenseDate(request.expenseDate());
            line.setCategory(category);
            line.setDescription(description);
            line.setAmount(amount);
            result.add(lineRepository.save(line));
        }
        LocalDateTime now = LocalDateTime.now(IST);
        for (ExpenseClaimLine old : existing) {
            if (!kept.contains(old.getId())) {
                old.setDeleted(true);
                old.setDeletedAt(now);
                lineRepository.save(old);
            }
        }
        return result;
    }

    static BigDecimal mileageAmount(BigDecimal km, BigDecimal rate) {
        return km.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal total(Collection<ExpenseClaimLine> lines) {
        return lines.stream().map(ExpenseClaimLine::getAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private List<PolicyIssue> policyIssues(List<ExpenseClaimLine> lines) {
        List<ExpensePolicyEvaluator.PolicyLine> policyLines = new ArrayList<>();
        int number = 0;
        for (ExpenseClaimLine line : lines) {
            policyLines.add(new ExpensePolicyEvaluator.PolicyLine(line.getId(), ++number, line.getCategory(),
                    line.getAmount(), line.hasReceipt()));
        }
        return ExpensePolicyEvaluator.evaluate(policyLines);
    }

    private ExpenseClaim ownDraft(Long id) {
        Employee me = requireSelf();
        ExpenseClaim claim = loadClaim(id);
        if (!isOwner(claim, me)) throw new AccessDeniedException("You can only change your own expense claims.");
        if (!ExpenseClaim.STATUS_DRAFT.equals(claim.getStatus())) {
            throw new BadRequestException("Only draft claims can be changed (current status: " + claim.getStatus() + ").");
        }
        return claim;
    }

    private ExpenseClaimLine lineOf(ExpenseClaim claim, Long lineId) {
        return lineRepository.findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(claim.getId()).stream()
                .filter(line -> line.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Expense line not found."));
    }

    private ExpenseClaim loadClaim(Long id) {
        return claimRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Expense claim not found."));
    }

    /** Any reviewer action: must be a linked employee and never on their own claim. */
    private Employee requireApprover(ExpenseClaim claim) {
        Employee me = currentEmployee();
        if (me == null) throw new AccessDeniedException("Your login is not linked to an employee profile.");
        if (isOwner(claim, me)) throw new AccessDeniedException("You can't approve or reject your own expense claim.");
        return me;
    }

    private void requireFinance() {
        if (!authorizationService.hasOrganizationScope("EXPENSE_MANAGE")) {
            throw new AccessDeniedException("Final approval needs the EXPENSE_MANAGE permission with organization scope.");
        }
    }

    private boolean canView(ExpenseClaim claim, Employee me) {
        if (isOwner(claim, me)) return true;
        if (authorizationService.hasOrganizationScope("EXPENSE_MANAGE")) return true;
        return isManagerApprover(claim, me);
    }

    boolean isManagerApprover(ExpenseClaim claim, Employee me) {
        if (me == null || isOwner(claim, me)) return false;
        return isReportingManager(claim, me)
                || authorizationService.isAllowed("EXPENSE_APPROVE", "EMPLOYEE", claim.getEmployee().getId());
    }

    private static boolean isReportingManager(ExpenseClaim claim, Employee me) {
        Employee manager = claim.getEmployee().getReportingManager();
        return me != null && manager != null && Objects.equals(manager.getId(), me.getId());
    }

    private static boolean hasActiveManager(Employee employee) {
        Employee manager = employee.getReportingManager();
        return manager != null && !manager.isDeleted();
    }

    private static boolean isOwner(ExpenseClaim claim, Employee me) {
        return me != null && claim.getEmployee() != null && Objects.equals(claim.getEmployee().getId(), me.getId());
    }

    private Employee currentEmployee() {
        return authorizationService.resolveCurrentEmployee();
    }

    private Employee requireSelf() {
        Employee me = currentEmployee();
        if (me == null) throw new BadRequestException("Your login is not linked to an active employee profile.");
        return me;
    }

    private void notifyEmployee(ExpenseClaim claim, String type, String title, String message) {
        Employee employee = claim.getEmployee();
        if (employee.getUser() == null) return;
        notificationService.notifyUser(claim.getCompany().getId(), employee.getUser().getId(), type, title,
                truncate(message, 1000), ENTITY, claim.getId(), "NORMAL");
    }

    private Map<Long, List<ExpenseClaimLine>> linesByClaim(List<Long> claimIds) {
        if (claimIds.isEmpty()) return Map.of();
        Map<Long, List<ExpenseClaimLine>> grouped = new LinkedHashMap<>();
        for (ExpenseClaimLine line : lineRepository.findAllByClaim_IdInAndDeletedFalseOrderByExpenseDateAscIdAsc(claimIds)) {
            grouped.computeIfAbsent(line.getClaim().getId(), key -> new ArrayList<>()).add(line);
        }
        return grouped;
    }

    private List<ClaimView> toViews(List<ExpenseClaim> claims) {
        Map<Long, List<ExpenseClaimLine>> lines = linesByClaim(claims.stream().map(ExpenseClaim::getId).toList());
        return claims.stream().map(claim -> toView(claim, lines.getOrDefault(claim.getId(), List.of()))).toList();
    }

    private ClaimView toView(ExpenseClaim claim, List<ExpenseClaimLine> lines) {
        Employee employee = claim.getEmployee();
        List<LineView> lineViews = lines.stream().map(line -> new LineView(line.getId(), line.getExpenseDate(),
                line.getCategory().getId(), line.getCategory().getName(), line.getDescription(), line.getAmount(),
                line.getMileageKm(), line.getMileageRate(), line.hasReceipt(), line.getReceiptFileName())).toList();
        return new ClaimView(claim.getId(), employee.getId(), employee.getFullName(), employee.getEmployeeCode(),
                employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                claim.getTitle(), claim.getPurpose(), claim.getStatus(), claim.getTotalAmount(),
                ist(claim.getCreatedAt()), ist(claim.getSubmittedAt()),
                nameOf(claim.getManagerApprovedBy()), ist(claim.getManagerApprovedAt()), claim.getManagerNote(),
                nameOf(claim.getApprovedBy()), ist(claim.getApprovedAt()), claim.getApproverNote(),
                nameOf(claim.getRejectedBy()), ist(claim.getRejectedAt()), claim.getRejectionReason(), claim.getRejectedStage(),
                ist(claim.getReimbursedAt()), claim.getReimbursementMethod(), claim.getPaymentReference(), claim.getPayrollItemId(),
                policyIssues(lines), lineViews);
    }

    private static String nameOf(Employee employee) {
        return employee == null ? null : employee.getFullName();
    }

    static OffsetDateTime ist(LocalDateTime value) {
        return value == null ? null : value.atZone(IST).toOffsetDateTime();
    }

    static String csvCell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private static Long requiredTenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("A company context is required.");
        return companyId;
    }
}
