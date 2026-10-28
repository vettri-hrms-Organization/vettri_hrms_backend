package com.haodaone.expense.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.expense.dto.ExpenseDtos.ClaimRequest;
import com.haodaone.expense.dto.ExpenseDtos.ClaimView;
import com.haodaone.expense.dto.ExpenseDtos.LineRequest;
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
import com.haodaone.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseClaimServiceTest {

    private static final Long COMPANY_ID = 42L;

    @Mock private ExpenseClaimRepository claimRepository;
    @Mock private ExpenseClaimLineRepository lineRepository;
    @Mock private ExpenseCategoryRepository categoryRepository;
    @Mock private ExpenseCategoryService categoryService;
    @Mock private AttachmentStorageService storageService;
    @Mock private AuthorizationService authorizationService;
    @Mock private AuditLogService auditLogService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private ExpenseClaimService service;

    private Company company;
    private Employee employee;
    private Employee manager;
    private Employee finance;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(COMPANY_ID);
        company = new Company();
        company.setId(COMPANY_ID);
        manager = employee(200L, "Meena", "Iyer");
        User managerLogin = new User();
        managerLogin.setId(9L);
        manager.setUser(managerLogin);
        employee = employee(100L, "Arun", "Kumar");
        employee.setReportingManager(manager);
        finance = employee(300L, "Lakshmi", "Nair");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Employee employee(Long id, String first, String last) {
        Employee e = new Employee();
        e.setId(id);
        e.setFirstName(first);
        e.setLastName(last);
        e.setCompany(company);
        return e;
    }

    private ExpenseClaim claim(Long id, String status) {
        ExpenseClaim claim = new ExpenseClaim();
        claim.setId(id);
        claim.setCompany(company);
        claim.setEmployee(employee);
        claim.setTitle("Client visit");
        claim.setStatus(status);
        claim.setTotalAmount(new BigDecimal("1000.00"));
        return claim;
    }

    private ExpenseClaimLine line(ExpenseClaim claim, ExpenseCategory category, String amount, boolean receipt) {
        ExpenseClaimLine line = new ExpenseClaimLine();
        line.setId(11L);
        line.setClaim(claim);
        line.setCategory(category);
        line.setExpenseDate(LocalDate.of(2026, 10, 1));
        line.setAmount(new BigDecimal(amount));
        if (receipt) line.setReceiptS3Key("expense-receipts/42/100/5/r.pdf");
        return line;
    }

    private ExpenseCategory category(String receiptAbove, String receiptAction) {
        ExpenseCategory category = new ExpenseCategory();
        category.setId(7L);
        category.setName("Travel");
        category.setReceiptRequiredAbove(receiptAbove == null ? null : new BigDecimal(receiptAbove));
        category.setReceiptAction(receiptAction);
        return category;
    }

    @Test
    void employeeCannotApproveTheirOwnClaim() {
        ExpenseClaim claim = claim(5L, ExpenseClaim.STATUS_SUBMITTED);
        when(claimRepository.findByIdAndCompany_IdAndDeletedFalse(5L, COMPANY_ID)).thenReturn(Optional.of(claim));
        when(authorizationService.resolveCurrentEmployee()).thenReturn(employee);

        assertThatThrownBy(() -> service.managerApprove(5L, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("own");
        assertThat(claim.getStatus()).isEqualTo(ExpenseClaim.STATUS_SUBMITTED);
    }

    @Test
    void blockingReceiptRuleStopsSubmission() {
        ExpenseClaim claim = claim(5L, ExpenseClaim.STATUS_DRAFT);
        when(authorizationService.resolveCurrentEmployee()).thenReturn(employee);
        when(claimRepository.findByIdAndCompany_IdAndDeletedFalse(5L, COMPANY_ID)).thenReturn(Optional.of(claim));
        when(lineRepository.findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(5L))
                .thenReturn(List.of(line(claim, category("500", "BLOCK"), "1000.00", false)));

        assertThatThrownBy(() -> service.submit(5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("receipt");
        assertThat(claim.getStatus()).isEqualTo(ExpenseClaim.STATUS_DRAFT);
        verifyNoInteractions(notificationService);
    }

    @Test
    void submissionWithWarningsGoesToTheReportingManager() {
        ExpenseClaim claim = claim(5L, ExpenseClaim.STATUS_DRAFT);
        when(authorizationService.resolveCurrentEmployee()).thenReturn(employee);
        when(claimRepository.findByIdAndCompany_IdAndDeletedFalse(5L, COMPANY_ID)).thenReturn(Optional.of(claim));
        when(lineRepository.findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(5L))
                .thenReturn(List.of(line(claim, category("500", "WARN"), "1000.00", false)));

        ClaimView view = service.submit(5L);

        assertThat(view.status()).isEqualTo(ExpenseClaim.STATUS_SUBMITTED);
        assertThat(claim.getSubmittedAt()).isNotNull();
        assertThat(claim.getPolicyWarnings()).contains("receipt");
        assertThat(view.policyIssues()).hasSize(1);
        verify(notificationService).notifyUser(eq(COMPANY_ID), eq(9L), eq("EXPENSE_SUBMITTED"), anyString(), anyString(),
                eq("ExpenseClaim"), eq(5L), eq("NORMAL"));
    }

    @Test
    void reportingManagerApprovesAndFinanceIsNotified() {
        ExpenseClaim claim = claim(5L, ExpenseClaim.STATUS_SUBMITTED);
        when(claimRepository.findByIdAndCompany_IdAndDeletedFalse(5L, COMPANY_ID)).thenReturn(Optional.of(claim));
        when(authorizationService.resolveCurrentEmployee()).thenReturn(manager);
        when(lineRepository.findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(5L)).thenReturn(List.of());

        ClaimView view = service.managerApprove(5L, "Looks fine");

        assertThat(view.status()).isEqualTo(ExpenseClaim.STATUS_MANAGER_APPROVED);
        assertThat(claim.getManagerApprovedBy()).isSameAs(manager);
        assertThat(claim.getManagerNote()).isEqualTo("Looks fine");
        verify(notificationService).notifyPermissionRecipients(eq(COMPANY_ID), eq(100L), eq("EXPENSE_MANAGE"),
                eq("EXPENSE_AWAITING_FINANCE"), anyString(), anyString(), eq("ExpenseClaim"), eq(5L));
    }

    @Test
    void financeCannotSkipTheManagerStageWhenAManagerExists() {
        ExpenseClaim claim = claim(5L, ExpenseClaim.STATUS_SUBMITTED);
        when(claimRepository.findByIdAndCompany_IdAndDeletedFalse(5L, COMPANY_ID)).thenReturn(Optional.of(claim));
        when(authorizationService.resolveCurrentEmployee()).thenReturn(finance);
        when(authorizationService.hasOrganizationScope("EXPENSE_MANAGE")).thenReturn(true);

        assertThatThrownBy(() -> service.finalApprove(5L, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reporting manager");
    }

    @Test
    void rejectionNeedsAReason() {
        assertThatThrownBy(() -> service.reject(5L, "  "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reason");
        verify(claimRepository, never()).findByIdAndCompany_IdAndDeletedFalse(anyLong(), anyLong());
    }

    @Test
    void mileageLinesAreComputedFromTheCompanyRate() {
        ExpenseCategory mileage = category(null, "WARN");
        mileage.setMileageRatePerKm(new BigDecimal("10.00"));
        when(authorizationService.resolveCurrentEmployee()).thenReturn(employee);
        when(claimRepository.save(any(ExpenseClaim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lineRepository.save(any(ExpenseClaimLine.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.findByIdAndCompany_IdAndDeletedFalse(7L, COMPANY_ID)).thenReturn(Optional.of(mileage));

        ClaimView view = service.create(new ClaimRequest("Site visits", null, List.of(
                new LineRequest(null, LocalDate.now().minusDays(1), 7L, "Office to site", null,
                        new BigDecimal("12.5"), new BigDecimal("99.00")))));

        assertThat(view.lines()).hasSize(1);
        assertThat(view.lines().get(0).amount()).isEqualByComparingTo("125.00");
        assertThat(view.lines().get(0).mileageRate()).isEqualByComparingTo("10.00");
        assertThat(view.totalAmount()).isEqualByComparingTo("125.00");
        assertThat(view.status()).isEqualTo(ExpenseClaim.STATUS_DRAFT);
    }

    @Test
    void futureDatedLinesAreRejected() {
        when(authorizationService.resolveCurrentEmployee()).thenReturn(employee);
        when(claimRepository.save(any(ExpenseClaim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.create(new ClaimRequest("Trip", null, List.of(
                new LineRequest(null, LocalDate.now().plusDays(5), 7L, null, new BigDecimal("10"), null, null)))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("future");
    }

    @Test
    void payrollHookReturnsOnlyClaimsApprovedByTheEndOfThePeriod() {
        ExpenseClaim october = claim(5L, ExpenseClaim.STATUS_APPROVED);
        october.setApprovedAt(LocalDateTime.of(2026, 10, 31, 18, 0));
        ExpenseClaim november = claim(6L, ExpenseClaim.STATUS_APPROVED);
        november.setApprovedAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        when(categoryService.reimbursementMode(COMPANY_ID)).thenReturn(ExpenseSettings.MODE_PAYROLL);
        when(claimRepository.findAllByCompany_IdAndEmployee_IdAndStatusAndReimbursedAtIsNullAndPayrollItemIdIsNullAndDeletedFalseOrderByIdAsc(
                COMPANY_ID, 100L, ExpenseClaim.STATUS_APPROVED)).thenReturn(List.of(october, november));

        List<ReimbursementPayable> payables = service.approvedUnreimbursed(COMPANY_ID, 100L, YearMonth.of(2026, 10));

        assertThat(payables).extracting(ReimbursementPayable::claimId).containsExactly(5L);
        assertThat(payables.get(0).taxable()).isFalse();
        assertThat(payables.get(0).amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void payrollHookIsEmptyWhenCompanyPaysSeparately() {
        when(categoryService.reimbursementMode(COMPANY_ID)).thenReturn(ExpenseSettings.MODE_SEPARATE);

        assertThat(service.approvedUnreimbursed(COMPANY_ID, 100L, YearMonth.of(2026, 10))).isEmpty();
        verifyNoInteractions(claimRepository);
    }

    @Test
    void markPaidLinksClaimsToThePayrollItemAndSkipsOthers() {
        ExpenseClaim approved = claim(5L, ExpenseClaim.STATUS_APPROVED);
        ExpenseClaim rejected = claim(6L, ExpenseClaim.STATUS_REJECTED);
        when(claimRepository.findAllByCompany_IdAndIdInAndDeletedFalse(eq(COMPANY_ID), any())).thenReturn(List.of(approved, rejected));

        service.markPaid(List.of(5L, 6L), 777L);

        assertThat(approved.getStatus()).isEqualTo(ExpenseClaim.STATUS_REIMBURSED);
        assertThat(approved.getPayrollItemId()).isEqualTo(777L);
        assertThat(approved.getReimbursementMethod()).isEqualTo(ExpenseClaim.METHOD_PAYROLL);
        assertThat(rejected.getStatus()).isEqualTo(ExpenseClaim.STATUS_REJECTED);
        assertThat(rejected.getPayrollItemId()).isNull();
    }
}
