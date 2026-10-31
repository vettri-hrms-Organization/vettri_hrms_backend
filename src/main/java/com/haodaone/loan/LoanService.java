package com.haodaone.loan;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.salary.adjustment.PayrollAdjustment;
import com.haodaone.salary.adjustment.PayrollDeductionProvider;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Employee loans and salary advances (Keka-style). Employees request; payroll admins (SALARY_MANAGE) approve
 * with the terms, pay out (outside payroll or as a non-taxable line in the next payroll), and recovery happens
 * automatically as a post-tax EMI on each payslip. HR can defer an EMI, record a part-payment or close early.
 *
 * <p>Payroll talks to it through {@link LoanPayrollHooks}: EMIs as post-tax deductions and pay-outs
 * through payroll as non-taxable earnings.</p>
 */
@Service
public class LoanService {

    static final String SOURCE_EMI = "LOAN_EMI";
    static final String SOURCE_DISBURSAL = "LOAN_DISBURSAL";
    static final String SOURCE_OUTSTANDING = "LOAN_OUTSTANDING";
    static final BigDecimal MAX_AMOUNT = new BigDecimal("5000000");
    static final int MAX_LOAN_MONTHS = 84;
    static final int MAX_ADVANCE_MONTHS = 12;
    private static final Set<String> RUNNING = Set.of(EmployeeLoan.APPROVED, EmployeeLoan.ACTIVE);
    private static final Set<String> OPEN_INSTALLMENT = Set.of(EmployeeLoanInstallment.PENDING);

    private final EmployeeLoanRepository loans;
    private final EmployeeLoanInstallmentRepository installments;
    private final EmployeeRepository employees;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public LoanService(EmployeeLoanRepository loans, EmployeeLoanInstallmentRepository installments, EmployeeRepository employees,
                       NotificationService notificationService, AuditLogService auditLogService, Clock applicationClock) {
        this.loans = loans;
        this.installments = installments;
        this.employees = employees;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.clock = applicationClock;
    }

    // ------------------------------------------------------------------ DTOs

    public record InstallmentDTO(Long id, int sequenceNo, String dueMonth, BigDecimal principal, BigDecimal interest,
                                 BigDecimal amount, String status, LocalDate paidOn, String note) { }
    public record LoanDTO(Long id, Long employeeId, String employeeName, String employeeCode, String loanType, String status,
                          BigDecimal requestedAmount, int requestedMonths, String reason, BigDecimal approvedAmount,
                          Integer tenureMonths, BigDecimal annualInterestRate, BigDecimal emiAmount, String firstDeductionMonth,
                          boolean disburseViaPayroll, LocalDate disbursedOn, String disbursementReference, String reviewedBy,
                          LocalDateTime reviewedAt, String reviewNote, LocalDate closedOn, BigDecimal recovered,
                          BigDecimal outstanding, BigDecimal totalInterest, String nextDueMonth, LocalDateTime requestedAt,
                          List<InstallmentDTO> schedule) { }
    public record RequestBody(String loanType, BigDecimal amount, Integer months, String reason) { }
    public record ApproveBody(BigDecimal amount, Integer months, BigDecimal annualInterestRate, String firstDeductionMonth,
                              Boolean disburseViaPayroll, String note) { }
    public record CreateBody(Long employeeId, String loanType, BigDecimal amount, Integer months, BigDecimal annualInterestRate,
                             String firstDeductionMonth, Boolean disburseViaPayroll, String reason) { }
    public record DisburseBody(LocalDate disbursedOn, String reference) { }
    public record PaymentBody(BigDecimal amount, LocalDate paidOn, String note) { }
    public record Preview(BigDecimal emi, BigDecimal totalInterest, BigDecimal totalPayable, List<InstallmentDTO> schedule) { }

    // ------------------------------------------------------------------ employee

    @Transactional(readOnly = true)
    public List<LoanDTO> mine() {
        Employee me = currentEmployee();
        return loans.findAllByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(me.getId()).stream().map(l -> toDto(l, me)).toList();
    }

    @Transactional
    public LoanDTO request(RequestBody r) {
        Employee me = currentEmployee();
        String type = type(r == null ? null : r.loanType());
        BigDecimal amount = amount(r.amount());
        int months = months(type, r.months());
        EmployeeLoan l = new EmployeeLoan();
        l.setCompanyId(me.getCompany().getId());
        l.setEmployeeId(me.getId());
        l.setLoanType(type);
        l.setStatus(EmployeeLoan.REQUESTED);
        l.setRequestedAmount(amount);
        l.setRequestedMonths(months);
        l.setReason(text(r.reason(), 1000));
        l = loans.save(l);
        auditLogService.log("LOAN", l.getId(), "REQUESTED", me.getFullName() + " requested a " + label(type) + " of Rs " + amount);
        notificationService.notifyPermissionRecipients(me.getCompany().getId(), me.getId(), "SALARY_MANAGE", "LOAN_REQUESTED",
                label(type) + " request", me.getFullName() + " requested a " + label(type) + " of Rs " + amount.toPlainString()
                        + " over " + months + " month" + (months == 1 ? "" : "s") + ".", "LOAN", l.getId());
        return toDto(l, me);
    }

    @Transactional
    public LoanDTO cancelMine(Long id) {
        Employee me = currentEmployee();
        EmployeeLoan l = load(id);
        if (!l.getEmployeeId().equals(me.getId())) throw new BadRequestException("Not your request");
        if (!EmployeeLoan.REQUESTED.equals(l.getStatus())) throw new BadRequestException("Only a request that isn't approved yet can be cancelled");
        l.setStatus(EmployeeLoan.CANCELLED);
        loans.save(l);
        auditLogService.log("LOAN", l.getId(), "CANCELLED", "Cancelled by the employee");
        return toDto(l, me);
    }

    /** EMI and schedule for given terms, so people see the cost before asking or approving. */
    public Preview preview(BigDecimal amount, Integer months, BigDecimal rate, String firstMonth) {
        BigDecimal principal = amount(amount);
        int n = months(EmployeeLoan.LOAN, months);
        BigDecimal r = rate(rate);
        YearMonth start = firstMonth == null || firstMonth.isBlank() ? YearMonth.now(clock).plusMonths(1) : month(firstMonth);
        List<EmployeeLoanInstallment> rows = schedule(null, null, principal, n, r, start, 1);
        BigDecimal interest = rows.stream().map(EmployeeLoanInstallment::getInterest).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Preview(rows.get(0).getAmount(), interest, principal.add(interest), rows.stream().map(LoanService::toDto).toList());
    }

    // ------------------------------------------------------------------ payroll admin (SALARY_MANAGE)

    @Transactional(readOnly = true)
    public List<LoanDTO> list(String status) {
        Long companyId = tenant();
        List<EmployeeLoan> rows = loans.findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(companyId).stream()
                .filter(l -> status == null || status.isBlank() || status.equalsIgnoreCase(l.getStatus())
                        || ("OPEN".equalsIgnoreCase(status) && (RUNNING.contains(l.getStatus()) || EmployeeLoan.REQUESTED.equals(l.getStatus()))))
                .toList();
        Map<Long, Employee> people = people(companyId, rows);
        return rows.stream().filter(l -> people.containsKey(l.getEmployeeId())).map(l -> toDto(l, people.get(l.getEmployeeId()))).toList();
    }

    @Transactional(readOnly = true)
    public LoanDTO get(Long id) {
        EmployeeLoan l = load(id);
        return toDto(l, employee(l.getEmployeeId()));
    }

    @Transactional
    public LoanDTO approve(Long id, ApproveBody b, String actor) {
        EmployeeLoan l = load(id);
        if (!EmployeeLoan.REQUESTED.equals(l.getStatus())) throw new BadRequestException("This request has already been handled");
        Employee e = employee(l.getEmployeeId());
        Employee me = currentEmployeeOrNull();
        if (me != null && me.getId().equals(e.getId())) throw new BadRequestException("You can't approve your own loan");
        applyTerms(l, b == null ? null : b.amount(), b == null ? null : b.months(), b == null ? null : b.annualInterestRate(),
                b == null ? null : b.firstDeductionMonth(), b != null && Boolean.TRUE.equals(b.disburseViaPayroll()));
        l.setStatus(EmployeeLoan.APPROVED);
        l.setReviewedBy(actor);
        l.setReviewedAt(LocalDateTime.now(clock));
        l.setReviewNote(text(b == null ? null : b.note(), 1000));
        loans.save(l);
        auditLogService.log("LOAN", l.getId(), "APPROVED", actor + " approved " + e.getFullName() + "'s " + label(l.getLoanType())
                + " of Rs " + l.getApprovedAmount() + " (" + l.getTenureMonths() + " months)");
        notifyEmployee(e, label(l.getLoanType()) + " approved", "Your " + label(l.getLoanType()) + " of Rs " + l.getApprovedAmount().toPlainString()
                + " is approved. EMI Rs " + l.getEmiAmount().toPlainString() + " from " + l.getFirstDeductionMonth()
                + (l.isDisburseViaPayroll() ? "; it will be paid with your next salary." : "."), l.getId());
        return toDto(l, e);
    }

    @Transactional
    public LoanDTO reject(Long id, String note, String actor) {
        EmployeeLoan l = load(id);
        if (!EmployeeLoan.REQUESTED.equals(l.getStatus())) throw new BadRequestException("This request has already been handled");
        String reason = text(note, 1000);
        if (reason == null) throw new BadRequestException("Add a note so the employee knows why");
        l.setStatus(EmployeeLoan.REJECTED);
        l.setReviewedBy(actor);
        l.setReviewedAt(LocalDateTime.now(clock));
        l.setReviewNote(reason);
        loans.save(l);
        Employee e = employee(l.getEmployeeId());
        auditLogService.log("LOAN", l.getId(), "REJECTED", actor + " declined " + e.getFullName() + "'s " + label(l.getLoanType()));
        notifyEmployee(e, label(l.getLoanType()) + " declined", "Your " + label(l.getLoanType()) + " request was declined: " + reason, l.getId());
        return toDto(l, e);
    }

    /** HR records a loan directly (e.g. agreed offline); it starts APPROVED. */
    @Transactional
    public LoanDTO create(CreateBody b, String actor) {
        if (b == null || b.employeeId() == null) throw new BadRequestException("Choose an employee");
        Employee e = employee(b.employeeId());
        Employee me = currentEmployeeOrNull();
        if (me != null && me.getId().equals(e.getId())) throw new BadRequestException("Ask a colleague to add your own loan");
        String type = type(b.loanType());
        EmployeeLoan l = new EmployeeLoan();
        l.setCompanyId(e.getCompany().getId());
        l.setEmployeeId(e.getId());
        l.setLoanType(type);
        l.setRequestedAmount(amount(b.amount()));
        l.setRequestedMonths(months(type, b.months()));
        l.setReason(text(b.reason(), 1000));
        l.setStatus(EmployeeLoan.APPROVED);
        l.setReviewedBy(actor);
        l.setReviewedAt(LocalDateTime.now(clock));
        l = loans.save(l);
        applyTerms(l, b.amount(), b.months(), b.annualInterestRate(), b.firstDeductionMonth(), Boolean.TRUE.equals(b.disburseViaPayroll()));
        loans.save(l);
        auditLogService.log("LOAN", l.getId(), "CREATED", actor + " added a " + label(type) + " of Rs " + l.getApprovedAmount() + " for " + e.getFullName());
        notifyEmployee(e, label(type) + " added", "A " + label(type) + " of Rs " + l.getApprovedAmount().toPlainString()
                + " was added for you. EMI Rs " + l.getEmiAmount().toPlainString() + " from " + l.getFirstDeductionMonth() + ".", l.getId());
        return toDto(l, e);
    }

    /** Money paid out by bank transfer/cheque outside payroll. */
    @Transactional
    public LoanDTO markDisbursed(Long id, DisburseBody b, String actor) {
        EmployeeLoan l = load(id);
        if (!EmployeeLoan.APPROVED.equals(l.getStatus())) throw new BadRequestException("Only an approved loan can be marked as paid out");
        if (l.isDisburseViaPayroll()) throw new BadRequestException("This loan is paid out with the next payroll run");
        LocalDate on = b == null || b.disbursedOn() == null ? LocalDate.now(clock) : b.disbursedOn();
        if (on.isAfter(LocalDate.now(clock))) throw new BadRequestException("The payout date can't be in the future");
        l.setDisbursedOn(on);
        l.setDisbursementReference(text(b == null ? null : b.reference(), 100));
        l.setDisbursalSettled(true);
        l.setStatus(EmployeeLoan.ACTIVE);
        loans.save(l);
        auditLogService.log("LOAN", l.getId(), "DISBURSED", actor + " marked the loan paid out on " + on);
        return get(id);
    }

    /** Skips one month's EMI: the installment moves to the end of the schedule. */
    @Transactional
    public LoanDTO defer(Long loanId, Long installmentId, String note, String actor) {
        EmployeeLoan l = load(loanId);
        if (!RUNNING.contains(l.getStatus())) throw new BadRequestException("Only a running loan can have an EMI deferred");
        List<EmployeeLoanInstallment> rows = installments.findAllByLoanIdAndDeletedFalseOrderBySequenceNoAsc(l.getId());
        EmployeeLoanInstallment target = rows.stream().filter(i -> i.getId().equals(installmentId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Installment not found"));
        if (!EmployeeLoanInstallment.PENDING.equals(target.getStatus())) throw new BadRequestException("Only a pending EMI can be deferred");
        String last = rows.stream().map(EmployeeLoanInstallment::getDueMonth).max(String::compareTo).orElse(target.getDueMonth());
        target.setStatus(EmployeeLoanInstallment.DEFERRED);
        target.setNote(text(note, 300));
        installments.save(target);
        EmployeeLoanInstallment moved = new EmployeeLoanInstallment();
        moved.setCompanyId(l.getCompanyId());
        moved.setLoanId(l.getId());
        moved.setSequenceNo(rows.stream().mapToInt(EmployeeLoanInstallment::getSequenceNo).max().orElse(0) + 1);
        moved.setDueMonth(YearMonth.parse(last).plusMonths(1).toString());
        moved.setPrincipal(target.getPrincipal());
        moved.setInterest(target.getInterest());
        moved.setAmount(target.getAmount());
        moved.setNote("Deferred from " + target.getDueMonth());
        installments.save(moved);
        auditLogService.log("LOAN", l.getId(), "EMI_DEFERRED", actor + " deferred the " + target.getDueMonth() + " EMI to " + moved.getDueMonth());
        return get(loanId);
    }

    /**
     * A repayment made outside payroll. Covers whole EMIs first (oldest first); any remainder reduces the
     * principal of the remaining schedule. Paying the full outstanding closes the loan.
     */
    @Transactional
    public LoanDTO recordPayment(Long loanId, PaymentBody b, String actor) {
        EmployeeLoan l = load(loanId);
        if (!EmployeeLoan.ACTIVE.equals(l.getStatus())) throw new BadRequestException("Payments can only be recorded on an active loan");
        BigDecimal remaining = amount(b == null ? null : b.amount());
        LocalDate on = b == null || b.paidOn() == null ? LocalDate.now(clock) : b.paidOn();
        List<EmployeeLoanInstallment> pending = installments.findAllByLoanIdAndDeletedFalseOrderBySequenceNoAsc(l.getId()).stream()
                .filter(i -> EmployeeLoanInstallment.PENDING.equals(i.getStatus())).toList();
        BigDecimal outstanding = pending.stream().map(EmployeeLoanInstallment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (remaining.compareTo(outstanding) > 0) throw new BadRequestException("That's more than the outstanding Rs " + outstanding.toPlainString());
        List<EmployeeLoanInstallment> left = new ArrayList<>();
        for (EmployeeLoanInstallment i : pending) {
            if (remaining.compareTo(i.getAmount()) >= 0) {
                remaining = remaining.subtract(i.getAmount());
                i.setStatus(EmployeeLoanInstallment.PAID_MANUALLY);
                i.setPaidOn(on);
                i.setNote(text(b.note(), 300));
                installments.save(i);
            } else {
                left.add(i);
            }
        }
        if (remaining.signum() > 0 && !left.isEmpty()) {
            // Spread the part-payment across the remaining EMIs' principal.
            BigDecimal perEmi = remaining.divide(BigDecimal.valueOf(left.size()), 2, RoundingMode.DOWN);
            BigDecimal rest = remaining.subtract(perEmi.multiply(BigDecimal.valueOf(left.size())));
            for (int k = 0; k < left.size(); k++) {
                EmployeeLoanInstallment i = left.get(k);
                BigDecimal cut = perEmi.add(k == left.size() - 1 ? rest : BigDecimal.ZERO);
                i.setPrincipal(i.getPrincipal().subtract(cut).max(BigDecimal.ZERO));
                i.setAmount(i.getAmount().subtract(cut).max(BigDecimal.ZERO));
                installments.save(i);
            }
        }
        auditLogService.log("LOAN", l.getId(), "PAYMENT", actor + " recorded a repayment of Rs " + b.amount() + " on " + on);
        closeIfDone(l);
        return get(loanId);
    }

    @Transactional
    public LoanDTO cancelApproved(Long id, String note, String actor) {
        EmployeeLoan l = load(id);
        if (!EmployeeLoan.APPROVED.equals(l.getStatus())) throw new BadRequestException("Only an approved loan that hasn't been paid out can be cancelled");
        l.setStatus(EmployeeLoan.CANCELLED);
        l.setReviewNote(text(note, 1000));
        loans.save(l);
        installments.findAllByLoanIdAndDeletedFalseOrderBySequenceNoAsc(l.getId()).forEach(i -> {
            i.setDeleted(true);
            i.setDeletedAt(LocalDateTime.now(clock));
            installments.save(i);
        });
        auditLogService.log("LOAN", l.getId(), "CANCELLED", actor + " cancelled the approved loan");
        return get(id);
    }

    // ------------------------------------------------------------------ payroll hooks

    /** EMIs due up to this payroll month (catch-up included) for loans that are paid out or paid out in this run. */
    @Transactional(readOnly = true)
    public List<PayrollDeductionProvider.PayrollDeduction> emisDue(Long companyId, Long employeeId, YearMonth period) {
        String upTo = period.toString();
        List<PayrollDeductionProvider.PayrollDeduction> out = new ArrayList<>();
        for (EmployeeLoan l : loans.findAllByCompanyIdAndEmployeeIdAndStatusInAndDeletedFalse(companyId, employeeId, RUNNING)) {
            boolean paidOut = EmployeeLoan.ACTIVE.equals(l.getStatus()) || l.isDisburseViaPayroll();
            if (!paidOut) continue;
            List<EmployeeLoanInstallment> rows = installments.findAllByLoanIdAndDeletedFalseOrderBySequenceNoAsc(l.getId());
            int total = (int) rows.stream().filter(i -> !EmployeeLoanInstallment.DEFERRED.equals(i.getStatus())).count();
            int k = 0;
            for (EmployeeLoanInstallment i : rows) {
                if (EmployeeLoanInstallment.DEFERRED.equals(i.getStatus())) continue;
                k++;
                if (!OPEN_INSTALLMENT.contains(i.getStatus()) || i.getDueMonth().compareTo(upTo) > 0 || i.getAmount().signum() <= 0) continue;
                out.add(new PayrollDeductionProvider.PayrollDeduction("LOAN_EMI",
                        (EmployeeLoan.ADVANCE.equals(l.getLoanType()) ? "Salary advance recovery " : "Loan EMI ") + k + "/" + total,
                        i.getAmount(), SOURCE_EMI, i.getId()));
            }
        }
        return out;
    }

    /** Loans to be paid out with this payroll, as a non-taxable earnings line. */
    @Transactional(readOnly = true)
    public List<PayrollAdjustment> payoutsDue(Long companyId, Long employeeId, YearMonth period) {
        return loans.findAllByCompanyIdAndEmployeeIdAndStatusInAndDeletedFalse(companyId, employeeId, Set.of(EmployeeLoan.APPROVED)).stream()
                .filter(l -> l.isDisburseViaPayroll() && !l.isDisbursalSettled())
                .map(l -> new PayrollAdjustment("LOAN_DISBURSAL", label(l.getLoanType()).substring(0, 1).toUpperCase()
                        + label(l.getLoanType()).substring(1) + " paid out", l.getApprovedAmount(), false, SOURCE_DISBURSAL, l.getId()))
                .toList();
    }

    @Transactional
    public void settlePayouts(List<PayrollAdjustment> adjustments, Long payrollItemId) {
        for (PayrollAdjustment a : adjustments) {
            if (!SOURCE_DISBURSAL.equals(a.sourceType())) continue;
            loans.findById(a.sourceId()).ifPresent(l -> {
                l.setDisbursalSettled(true);
                l.setDisbursedOn(LocalDate.now(clock));
                l.setDisbursementReference("Payroll item " + payrollItemId);
                l.setStatus(EmployeeLoan.ACTIVE);
                loans.save(l);
            });
        }
    }

    /** Payroll deducted these EMIs (PayrollDeductionProvider callback). */
    @Transactional
    public void settleEmis(List<PayrollDeductionProvider.PayrollDeduction> deductions, Long payrollItemId) {
        Set<Long> touched = new HashSet<>();
        for (PayrollDeductionProvider.PayrollDeduction d : deductions) {
            if (!SOURCE_EMI.equals(d.sourceType())) continue;
            installments.findById(d.sourceId()).ifPresent(i -> {
                i.setStatus(EmployeeLoanInstallment.DEDUCTED);
                i.setPayrollItemId(payrollItemId);
                i.setPaidOn(LocalDate.now(clock));
                installments.save(i);
                touched.add(i.getLoanId());
            });
        }
        touched.forEach(id -> loans.findById(id).ifPresent(this::closeIfDone));
    }

    /** Whole outstanding per running loan, for a leaving employee's full and final settlement. */
    @Transactional(readOnly = true)
    public List<PayrollDeductionProvider.PayrollDeduction> outstandingForSettlement(Long companyId, Long employeeId) {
        List<PayrollDeductionProvider.PayrollDeduction> out = new ArrayList<>();
        for (EmployeeLoan l : loans.findAllByCompanyIdAndEmployeeIdAndStatusInAndDeletedFalse(companyId, employeeId, RUNNING)) {
            if (!EmployeeLoan.ACTIVE.equals(l.getStatus()) && !l.isDisburseViaPayroll()) continue;
            BigDecimal owed = installments.findAllByLoanIdAndDeletedFalseOrderBySequenceNoAsc(l.getId()).stream()
                    .filter(i -> EmployeeLoanInstallment.PENDING.equals(i.getStatus()))
                    .map(EmployeeLoanInstallment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (owed.signum() > 0) {
                out.add(new PayrollDeductionProvider.PayrollDeduction("LOAN_RECOVERY",
                        (EmployeeLoan.ADVANCE.equals(l.getLoanType()) ? "Salary advance" : "Loan") + " outstanding recovered",
                        owed, SOURCE_OUTSTANDING, l.getId()));
            }
        }
        return out;
    }

    @Transactional
    public void recoverInSettlement(List<PayrollDeductionProvider.PayrollDeduction> recoveries, Long settlementMarker) {
        for (PayrollDeductionProvider.PayrollDeduction d : recoveries) {
            if (!SOURCE_OUTSTANDING.equals(d.sourceType())) continue;
            loans.findById(d.sourceId()).ifPresent(l -> {
                installments.findAllByLoanIdAndDeletedFalseOrderBySequenceNoAsc(l.getId()).stream()
                        .filter(i -> EmployeeLoanInstallment.PENDING.equals(i.getStatus()))
                        .forEach(i -> {
                            i.setStatus(EmployeeLoanInstallment.PAID_MANUALLY);
                            i.setPaidOn(LocalDate.now(clock));
                            i.setNote("Recovered in full and final settlement");
                            installments.save(i);
                        });
                if (EmployeeLoan.APPROVED.equals(l.getStatus())) l.setStatus(EmployeeLoan.ACTIVE);
                auditLogService.log("LOAN", l.getId(), "RECOVERED_IN_SETTLEMENT",
                        "Outstanding Rs " + d.amount() + " recovered in settlement " + (-settlementMarker));
                closeIfDone(l);
            });
        }
    }

    // ------------------------------------------------------------------ helpers

    private void applyTerms(EmployeeLoan l, BigDecimal amount, Integer months, BigDecimal rate, String firstMonth, boolean viaPayroll) {
        BigDecimal principal = amount == null ? l.getRequestedAmount() : amount(amount);
        int n = months(l.getLoanType(), months == null ? l.getRequestedMonths() : months);
        BigDecimal r = EmployeeLoan.ADVANCE.equals(l.getLoanType()) ? BigDecimal.ZERO : rate(rate);
        YearMonth current = YearMonth.now(clock);
        YearMonth start = firstMonth == null || firstMonth.isBlank() ? current.plusMonths(1) : month(firstMonth);
        if (start.isBefore(current)) throw new BadRequestException("The first EMI month can't be in the past");
        l.setApprovedAmount(principal);
        l.setTenureMonths(n);
        l.setAnnualInterestRate(r);
        l.setFirstDeductionMonth(start.toString());
        l.setDisburseViaPayroll(viaPayroll);
        List<EmployeeLoanInstallment> rows = schedule(l.getCompanyId(), l.getId(), principal, n, r, start, 1);
        l.setEmiAmount(rows.get(0).getAmount());
        installments.saveAll(rows);
    }

    /** Equal monthly installments; 0% = principal spread evenly, otherwise reducing-balance EMI. Last row absorbs rounding. */
    static List<EmployeeLoanInstallment> schedule(Long companyId, Long loanId, BigDecimal principal, int n, BigDecimal annualRate,
                                                  YearMonth start, int firstSeq) {
        List<EmployeeLoanInstallment> rows = new ArrayList<>();
        BigDecimal monthly = annualRate.divide(BigDecimal.valueOf(1200), 12, RoundingMode.HALF_UP);
        BigDecimal emi;
        if (monthly.signum() == 0) {
            emi = principal.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        } else {
            BigDecimal growth = BigDecimal.ONE.add(monthly).pow(n, MathContext.DECIMAL64);
            emi = principal.multiply(monthly).multiply(growth).divide(growth.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
        }
        BigDecimal balance = principal;
        for (int k = 0; k < n; k++) {
            BigDecimal interest = balance.multiply(monthly).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principalPart = k == n - 1 ? balance : emi.subtract(interest).min(balance);
            balance = balance.subtract(principalPart);
            EmployeeLoanInstallment i = new EmployeeLoanInstallment();
            i.setCompanyId(companyId);
            i.setLoanId(loanId);
            i.setSequenceNo(firstSeq + k);
            i.setDueMonth(start.plusMonths(k).toString());
            i.setPrincipal(principalPart);
            i.setInterest(interest);
            i.setAmount(principalPart.add(interest));
            rows.add(i);
        }
        return rows;
    }

    private void closeIfDone(EmployeeLoan l) {
        boolean open = installments.findAllByLoanIdAndDeletedFalseOrderBySequenceNoAsc(l.getId()).stream()
                .anyMatch(i -> EmployeeLoanInstallment.PENDING.equals(i.getStatus()) && i.getAmount().signum() > 0);
        if (!open && EmployeeLoan.ACTIVE.equals(l.getStatus())) {
            l.setStatus(EmployeeLoan.CLOSED);
            l.setClosedOn(LocalDate.now(clock));
            loans.save(l);
            auditLogService.log("LOAN", l.getId(), "CLOSED", "Fully recovered");
            employees.findById(l.getEmployeeId()).ifPresent(e -> notifyEmployee(e, label(l.getLoanType()) + " repaid",
                    "Your " + label(l.getLoanType()) + " is fully repaid. No more deductions will be made.", l.getId()));
        }
    }

    private LoanDTO toDto(EmployeeLoan l, Employee e) {
        List<EmployeeLoanInstallment> rows = l.getId() == null ? List.of() : installments.findAllByLoanIdAndDeletedFalseOrderBySequenceNoAsc(l.getId());
        BigDecimal recovered = rows.stream().filter(i -> EmployeeLoanInstallment.DEDUCTED.equals(i.getStatus())
                        || EmployeeLoanInstallment.PAID_MANUALLY.equals(i.getStatus()))
                .map(EmployeeLoanInstallment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = rows.stream().filter(i -> EmployeeLoanInstallment.PENDING.equals(i.getStatus()))
                .map(EmployeeLoanInstallment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal interest = rows.stream().filter(i -> !EmployeeLoanInstallment.DEFERRED.equals(i.getStatus()))
                .map(EmployeeLoanInstallment::getInterest).reduce(BigDecimal.ZERO, BigDecimal::add);
        String next = rows.stream().filter(i -> EmployeeLoanInstallment.PENDING.equals(i.getStatus()) && i.getAmount().signum() > 0)
                .map(EmployeeLoanInstallment::getDueMonth).min(String::compareTo).orElse(null);
        return new LoanDTO(l.getId(), e.getId(), e.getFullName(), e.getEmployeeCode(), l.getLoanType(), l.getStatus(),
                l.getRequestedAmount(), l.getRequestedMonths(), l.getReason(), l.getApprovedAmount(), l.getTenureMonths(),
                l.getAnnualInterestRate(), l.getEmiAmount(), l.getFirstDeductionMonth(), l.isDisburseViaPayroll(), l.getDisbursedOn(),
                l.getDisbursementReference(), l.getReviewedBy(), l.getReviewedAt(), l.getReviewNote(), l.getClosedOn(),
                recovered, outstanding, interest, next, l.getCreatedAt(), rows.stream().map(LoanService::toDto).toList());
    }

    private static InstallmentDTO toDto(EmployeeLoanInstallment i) {
        return new InstallmentDTO(i.getId(), i.getSequenceNo(), i.getDueMonth(), i.getPrincipal(), i.getInterest(), i.getAmount(),
                i.getStatus(), i.getPaidOn(), i.getNote());
    }

    private void notifyEmployee(Employee e, String title, String message, Long loanId) {
        if (e.getUser() == null) return;
        notificationService.notifyUser(e.getCompany().getId(), e.getUser().getId(), "LOAN", title, message, "LOAN", loanId, "NORMAL");
    }

    private static String type(String value) {
        String t = value == null ? EmployeeLoan.LOAN : value.trim().toUpperCase(Locale.ROOT);
        if (!EmployeeLoan.LOAN.equals(t) && !EmployeeLoan.ADVANCE.equals(t)) throw new BadRequestException("Type must be LOAN or ADVANCE");
        return t;
    }

    private static String label(String type) {
        return EmployeeLoan.ADVANCE.equals(type) ? "salary advance" : "loan";
    }

    private static BigDecimal amount(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new BadRequestException("Enter an amount greater than zero");
        if (value.compareTo(MAX_AMOUNT) > 0) throw new BadRequestException("The amount is above the Rs 50,00,000 limit");
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static int months(String type, Integer value) {
        int max = EmployeeLoan.ADVANCE.equals(type) ? MAX_ADVANCE_MONTHS : MAX_LOAN_MONTHS;
        if (value == null || value < 1 || value > max) throw new BadRequestException("Repayment must be between 1 and " + max + " months");
        return value;
    }

    private static BigDecimal rate(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        if (value.signum() < 0 || value.compareTo(new BigDecimal("36")) > 0) throw new BadRequestException("Interest must be between 0% and 36% a year");
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private static YearMonth month(String value) {
        try {
            return YearMonth.parse(value.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            throw new BadRequestException("Month must look like 2026-11");
        }
    }

    private static String text(String value, int max) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.length() > max) throw new BadRequestException("Keep it under " + max + " characters");
        return v;
    }

    private Map<Long, Employee> people(Long companyId, List<EmployeeLoan> rows) {
        Set<Long> ids = rows.stream().map(EmployeeLoan::getEmployeeId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return employees.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(companyId, ids).stream()
                .collect(Collectors.toMap(Employee::getId, x -> x));
    }

    private EmployeeLoan load(Long id) {
        return loans.findByIdAndCompanyIdAndDeletedFalse(id, tenant()).orElseThrow(() -> new ResourceNotFoundException("Loan not found"));
    }

    private Employee employee(Long id) {
        return employees.findByIdAndCompany_IdAndDeletedFalse(id, tenant()).orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    private Employee currentEmployeeOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof com.haodaone.security.CustomUserPrincipal principal)) return null;
        Long companyId = TenantContext.getCurrentTenant();
        return employees.findByUser_IdAndDeletedFalse(principal.getId())
                .filter(e -> e.getCompany() != null && e.getCompany().getId().equals(companyId)).orElse(null);
    }

    private Employee currentEmployee() {
        Employee me = currentEmployeeOrNull();
        if (me == null) throw new BadRequestException("Your login isn't linked to an employee record");
        return me;
    }

    private static Long tenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        return companyId;
    }
}
