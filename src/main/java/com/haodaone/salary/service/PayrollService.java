package com.haodaone.salary.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.salary.dto.*;
import com.haodaone.salary.entity.*;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.PayrollRunRepository;
import com.haodaone.salary.repository.SalaryStructureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.haodaone.tenant.TenantContext;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;

/**
 * Payroll Processing state machine: DRAFT (items generated from every
 * actively-paid employee, individually hold-able) -> PROCESSED (totals
 * locked in, items snapshot their structure permanently) -> PAID (payment
 * date recorded). Mirrors CandidateService's "explicit VALID transitions,
 * checked before every write" style rather than leaving state changes
 * implicit in controller code.
 */
@Service
public class PayrollService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final SalaryStructureRepository salaryStructureRepository;
    private final AuditLogService auditLogService;
    private final CompanyRepository companyRepository;

    public PayrollService(PayrollRunRepository payrollRunRepository, PayrollItemRepository payrollItemRepository,
                           SalaryStructureRepository salaryStructureRepository, AuditLogService auditLogService,
                           CompanyRepository companyRepository) {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.salaryStructureRepository = salaryStructureRepository;
        this.auditLogService = auditLogService;
        this.companyRepository = companyRepository;
    }

    public List<PayrollRunSummaryDTO> listRuns() {
        return payrollRunRepository.findAllByCompany_IdAndDeletedFalseOrderByPeriodYearDescPeriodMonthDesc(requiredTenant())
                .stream().map(PayrollRunSummaryDTO::from).toList();
    }

    public PayrollRunDTO getRun(Long runId) {
        PayrollRun run = findRunOrThrow(runId);
        List<PayrollItem> items = payrollItemRepository.findByPayrollRunIdAndDeletedFalseOrderByEmployee_FirstNameAsc(runId);
        return PayrollRunDTO.of(run, items);
    }

    /**
     * Opens a new DRAFT run for the given month and snapshots every
     * actively employed, actively-paid employee's current salary structure
     * into a PENDING PayrollItem. Only one run may exist per period.
     */
    @Transactional
    public PayrollRunDTO createRun(CreatePayrollRunRequest request) {
        Long companyId = requiredTenant();
        payrollRunRepository.findByPeriodYearAndPeriodMonthAndCompany_IdAndDeletedFalse(request.getPeriodYear(), request.getPeriodMonth(), companyId)
                .ifPresent(existing -> {
                    throw new BadRequestException("A payroll run already exists for " + request.getPeriodMonth() + "/" + request.getPeriodYear());
                });

        PayrollRun run = new PayrollRun();
        run.setPeriodMonth(request.getPeriodMonth());
        run.setPeriodYear(request.getPeriodYear());
        run.setPayDate(request.getPayDate());
        run.setRemarks(request.getRemarks());
        run.setStatus(PayrollRunStatus.DRAFT);
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        run.setCompany(company);
        PayrollRun savedRun = payrollRunRepository.save(run);

        List<SalaryStructure> activeStructures = salaryStructureRepository.findAllActiveByCompany(companyId);
        int included = 0;
        for (SalaryStructure structure : activeStructures) {
            Employee employee = structure.getEmployee();
            if (employee.isDeleted() || EmploymentStatus.RESIGNED.equals(employee.getStatus())
                    || EmploymentStatus.TERMINATED.equals(employee.getStatus())) {
                continue;
            }
            PayrollItem item = new PayrollItem();
            item.setPayrollRun(savedRun);
            item.setCompany(company);
            item.setEmployee(employee);
            item.setSalaryStructure(structure);
            item.setComponents(copyComponents(structure));
            item.recalculate();
            item.setStatus(PayrollItemStatus.PENDING);
            payrollItemRepository.save(item);
            included++;
        }

        savedRun.setTotalEmployees(included);
        payrollRunRepository.save(savedRun);

        auditLogService.log("PayrollRun", savedRun.getId(), "CREATE",
                "Opened payroll run for " + request.getPeriodMonth() + "/" + request.getPeriodYear() + " with " + included + " employee(s)");
        return getRun(savedRun.getId());
    }

    /** Toggles a single employee's line between PENDING and ON_HOLD while the run is still DRAFT. */
    @Transactional
    public PayrollItemDTO setItemHold(Long runId, Long itemId, UpdatePayrollItemRequest request) {
        PayrollRun run = findRunOrThrow(runId);
        if (!PayrollRunStatus.DRAFT.equals(run.getStatus())) {
            throw new BadRequestException("Employees can only be held or released while the run is still in DRAFT");
        }
        PayrollItem item = payrollItemRepository.findByIdAndDeletedFalse(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll item not found: " + itemId));
        if (!item.getPayrollRun().getId().equals(runId)) {
            throw new BadRequestException("That payroll item does not belong to this run");
        }

        item.setStatus(request.isOnHold() ? PayrollItemStatus.ON_HOLD : PayrollItemStatus.PENDING);
        item.setRemarks(request.getRemarks());
        PayrollItem saved = payrollItemRepository.save(item);

        auditLogService.log("PayrollItem", saved.getId(), request.isOnHold() ? "HOLD" : "RELEASE",
                (request.isOnHold() ? "Held" : "Released") + " '" + saved.getEmployee().getFullName() + "' in payroll run #" + runId);
        return PayrollItemDTO.from(saved);
    }

    /** DRAFT -> PROCESSED: locks in totals across every non-held item and stamps processedAt. */
    @Transactional
    public PayrollRunDTO process(Long runId) {
        PayrollRun run = findRunOrThrow(runId);
        if (!PayrollRunStatus.DRAFT.equals(run.getStatus())) {
            throw new BadRequestException("Only a DRAFT run can be processed");
        }

        List<PayrollItem> items = payrollItemRepository.findByPayrollRunIdAndDeletedFalseOrderByEmployee_FirstNameAsc(runId);
        if (items.isEmpty()) {
            throw new BadRequestException("This run has no employees to process");
        }

        for (PayrollItem item : items) {
            if (PayrollItemStatus.PENDING.equals(item.getStatus())) {
                item.setStatus(PayrollItemStatus.PROCESSED);
                payrollItemRepository.save(item);
            }
        }

        run.setTotalGross(payrollItemRepository.sumGrossSalaryForRun(runId));
        run.setTotalDeductions(payrollItemRepository.sumDeductionsForRun(runId));
        run.setTotalNet(payrollItemRepository.sumNetSalaryForRun(runId));
        run.setStatus(PayrollRunStatus.PROCESSED);
        run.setProcessedAt(LocalDateTime.now());
        PayrollRun saved = payrollRunRepository.save(run);

        auditLogService.log("PayrollRun", saved.getId(), "PROCESS",
                "Processed payroll for " + saved.getPeriodMonth() + "/" + saved.getPeriodYear()
                        + " - net payout " + saved.getTotalNet());
        return getRun(saved.getId());
    }

    /** PROCESSED -> PAID: stamps every processed item (and the run) with a payment date. */
    @Transactional
    public PayrollRunDTO markPaid(Long runId, MarkPaidRequest request) {
        PayrollRun run = findRunOrThrow(runId);
        if (!PayrollRunStatus.PROCESSED.equals(run.getStatus())) {
            throw new BadRequestException("Only a PROCESSED run can be marked as paid");
        }

        LocalDate paymentDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();
        List<PayrollItem> items = payrollItemRepository.findByPayrollRunIdAndDeletedFalseOrderByEmployee_FirstNameAsc(runId);
        for (PayrollItem item : items) {
            if (PayrollItemStatus.PROCESSED.equals(item.getStatus())) {
                item.setStatus(PayrollItemStatus.PAID);
                item.setPaymentDate(paymentDate);
                payrollItemRepository.save(item);
            }
        }

        run.setStatus(PayrollRunStatus.PAID);
        run.setPayDate(paymentDate);
        PayrollRun saved = payrollRunRepository.save(run);

        auditLogService.log("PayrollRun", saved.getId(), "MARK_PAID",
                "Marked payroll for " + saved.getPeriodMonth() + "/" + saved.getPeriodYear() + " as paid on " + paymentDate);
        return getRun(saved.getId());
    }

    /** DRAFT -> CANCELLED, for a run opened against the wrong period. Never allowed once processed. */
    @Transactional
    public void cancel(Long runId) {
        PayrollRun run = findRunOrThrow(runId);
        if (!PayrollRunStatus.DRAFT.equals(run.getStatus())) {
            throw new BadRequestException("Only a DRAFT run can be cancelled");
        }
        run.setStatus(PayrollRunStatus.CANCELLED);
        run.setDeleted(true);
        payrollRunRepository.save(run);

        List<PayrollItem> items = payrollItemRepository.findByPayrollRunIdAndDeletedFalseOrderByEmployee_FirstNameAsc(runId);
        items.forEach(item -> item.setDeleted(true));
        payrollItemRepository.saveAll(items);

        auditLogService.log("PayrollRun", run.getId(), "CANCEL",
                "Cancelled draft payroll run for " + run.getPeriodMonth() + "/" + run.getPeriodYear());
    }

    private SalaryComponents copyComponents(SalaryStructure structure) {
        return SalaryComponentsDTO.from(structure.getComponents()).toEntity();
    }

    private PayrollRun findRunOrThrow(Long runId) {
        return payrollRunRepository.findByIdAndCompany_IdAndDeletedFalse(runId, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found: " + runId));
    }

    private Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }
}
