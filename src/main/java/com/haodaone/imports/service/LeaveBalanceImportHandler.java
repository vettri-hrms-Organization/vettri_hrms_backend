package com.haodaone.imports.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.imports.entity.DataImportBatch;
import com.haodaone.imports.entity.OpeningLeaveBalance;
import com.haodaone.imports.repository.OpeningLeaveBalanceRepository;
import com.haodaone.leave.entity.LeaveBalance;
import com.haodaone.leave.entity.LeaveBalanceAdjustment;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.LeaveBalanceAdjustmentRepository;
import com.haodaone.leave.repository.LeaveBalanceRepository;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.leave.service.LeaveBalanceService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Opening leave balances per employee / leave type / year, applied through the leave policy module's ledger:
 * <ul>
 *   <li>default (ADJUSTMENT): a {@link LeaveBalanceAdjustment} with reason "Opening balance import" for the difference
 *       between the opening figure and the balance Vettri computes today, so the balance equals the opening figure as
 *       of the import date and accrues normally afterwards;</li>
 *   <li>CARRY_FORWARD: the figure becomes the year's carried-forward days (accrual for the year is added on top).</li>
 * </ul>
 * Idempotent: each key is recorded in {@link OpeningLeaveBalance}; importing it again is rejected unless "replace"
 * is chosen, which first reverses the earlier import (soft-deletes its adjustment / restores the carry-forward).
 */
@Component
public class LeaveBalanceImportHandler implements ImportHandler {

    public static final String REASON = "Opening balance import";
    static final String CODE = "Employee Code";
    static final String TYPE = "Leave Type Code";
    static final String YEAR = "Year";
    static final String DAYS = "Opening Balance";

    private static final List<ImportSheet.Column> COLUMNS = List.of(
            new ImportSheet.Column(CODE, true, "Existing employee code"),
            new ImportSheet.Column(TYPE, true, "Leave type code (or exact name) from Leave Configuration, e.g. CL, SL, EL"),
            new ImportSheet.Column(YEAR, true, "Leave year, e.g. 2026"),
            new ImportSheet.Column(DAYS, true, "Balance in days as of today (halves allowed, e.g. 7.5)"));

    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveBalanceAdjustmentRepository adjustmentRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final OpeningLeaveBalanceRepository openingRepository;
    private final AuditLogService auditLogService;

    public LeaveBalanceImportHandler(EmployeeRepository employeeRepository, LeaveTypeRepository leaveTypeRepository,
                                     LeaveBalanceRepository leaveBalanceRepository, LeaveBalanceAdjustmentRepository adjustmentRepository,
                                     LeaveBalanceService leaveBalanceService, OpeningLeaveBalanceRepository openingRepository,
                                     AuditLogService auditLogService) {
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.openingRepository = openingRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    public ImportType type() {
        return ImportType.LEAVE_BALANCES;
    }

    @Override
    public List<ImportSheet.Column> columns() {
        return COLUMNS;
    }

    @Override
    public List<List<String>> exampleRows() {
        int year = LeaveBalanceService.today().getYear();
        return List.of(
                List.of("EMP0001", "CL", String.valueOf(year), "6"),
                List.of("EMP0001", "EL", String.valueOf(year), "12.5"));
    }

    @Override
    public List<String> instructions() {
        return List.of(
                "One row per employee, leave type and year. Opening Balance is what the employee has left TODAY in your old system.",
                "Default: Vettri adds a balance adjustment (reason \"Opening balance import\") so the balance shown equals your figure today; accrual continues normally after that.",
                "Option 'As carried-forward days': the figure becomes the year's carried-forward balance and this year's accrual is added on top - use this at the start of a leave year.",
                "Importing the same employee / leave type / year again is rejected unless you tick 'Replace existing', which reverses the earlier import first - so re-uploading a file never double-counts.",
                "Comp-off balances cannot be imported. Negative balances are allowed only up to the leave type's negative balance limit.",
                "Requires Leave Manage permission with organization scope.");
    }

    record Payload(Long employeeId, Long leaveTypeId, int year, double days, String label) {
    }

    @Override
    public List<RowPlan> validate(Long companyId, List<ImportSheet.SheetRow> rows, ImportOptions options) {
        EmployeeLookup lookup = new EmployeeLookup(employeeRepository, companyId);
        Map<String, LeaveType> types = new HashMap<>();
        for (LeaveType type : leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId)) {
            if (type.getName() != null) types.putIfAbsent(type.getName().trim().toLowerCase(Locale.ROOT), type);
        }
        for (LeaveType type : leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId)) {
            if (type.getCode() != null) types.put(type.getCode().trim().toLowerCase(Locale.ROOT), type);
        }
        int currentYear = LeaveBalanceService.today().getYear();
        List<RowPlan> plans = new ArrayList<>();
        for (ImportSheet.SheetRow row : rows) {
            RowPlan plan = new RowPlan(row.number());
            ImportSheet.Reader r = new ImportSheet.Reader(row, plan);
            Employee employee = lookup.resolve(r.required(CODE), plan);
            String typeCode = r.required(TYPE);
            Integer year = r.integer(YEAR, true);
            Double days = r.number(DAYS, true);
            LeaveType type = null;
            if (typeCode != null) {
                type = types.get(typeCode.trim().toLowerCase(Locale.ROOT));
                if (type == null) plan.error("Leave type '" + typeCode + "' was not found");
                else if (!type.isActive()) plan.error("Leave type '" + typeCode + "' is inactive");
                else if (LeaveBalanceService.isCompOff(type)) plan.error("Comp-off balances cannot be imported");
            }
            if (year != null && (year < 2000 || year > currentYear + 1)) {
                plan.error("Year must be between 2000 and " + (currentYear + 1));
            }
            if (days != null) {
                days = Math.round(days * 100.0) / 100.0;
                if (Math.abs(days) > 366) plan.error("Opening Balance must be between -366 and 366 days");
                if (type != null && days < 0) {
                    double allowed = type.getNegativeBalanceDays() == null ? 0 : Math.max(0, type.getNegativeBalanceDays());
                    if (-days > allowed + 1e-9) plan.error("Negative balance is above the " + type.getName() + " limit (" + allowed + " day(s))");
                }
            }
            if (employee != null && type != null && year != null) {
                lookup.duplicate(employee.getId() + "|" + type.getId() + "|" + year, plan,
                        employee.getEmployeeCode() + " / " + typeCode + " / " + year);
                if (EmployeeLookup.exited(employee)) plan.warning("Employee has exited (" + employee.getStatus() + ")");
                OpeningLeaveBalance existing = openingRepository
                        .findFirstByCompany_IdAndEmployee_IdAndLeaveType_IdAndLeaveYearAndDeletedFalse(companyId, employee.getId(), type.getId(), year)
                        .orElse(null);
                if (existing != null && !options.replace()) {
                    plan.error("An opening balance (" + fmt(existing.getOpeningDays()) + " day(s)) was already imported for this employee, leave type and year"
                            + (existing.getBatch() != null ? " in batch #" + existing.getBatch().getId() : "")
                            + ". Tick 'Replace existing' to overwrite it.");
                }
                if (plan.valid() && days != null) {
                    LeaveBalanceService.BalanceDetail detail = leaveBalanceService.compute(companyId, employee, type, year, LeaveBalanceService.asOfFor(year));
                    double baseline = detail.remaining() - (existing == null ? 0 : existing.getAppliedDays());
                    double change;
                    if (options.carryForward()) {
                        double carriedBaseline = existing != null && OpeningLeaveBalance.MODE_CARRY_FORWARD.equals(existing.getApplyMode())
                                ? nz(existing.getPreviousCarriedDays()) : detail.carried();
                        change = days - carriedBaseline;
                    } else {
                        change = days - baseline;
                    }
                    plan.value("Current balance", fmt(round2(baseline)));
                    plan.value("Change", (change > 0 ? "+" : "") + fmt(round2(change)));
                    plan.action((existing != null ? "Replace earlier import: " : "")
                            + (options.carryForward() ? "Set carried-forward to " + fmt(days) : "Set balance to " + fmt(days)));
                    plan.payload(new Payload(employee.getId(), type.getId(), year, days,
                            employee.getFullName() + " / " + type.getName() + " / " + year));
                }
            }
            plans.add(plan);
        }
        return plans;
    }

    @Override
    public String apply(Long companyId, RowPlan plan, DataImportBatch batch, ImportOptions options) {
        Payload p = plan.payloadAs(Payload.class);
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(p.employeeId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        LeaveType type = leaveTypeRepository.findByIdAndCompany_IdAndDeletedFalse(p.leaveTypeId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));
        employeeRepository.lockById(employee.getId());
        OpeningLeaveBalance existing = openingRepository
                .findFirstByCompany_IdAndEmployee_IdAndLeaveType_IdAndLeaveYearAndDeletedFalse(companyId, employee.getId(), type.getId(), p.year())
                .orElse(null);
        if (existing != null) {
            if (!options.replace()) throw new BadRequestException("An opening balance was already imported for " + p.label());
            reverse(companyId, existing);
        }

        OpeningLeaveBalance entry = new OpeningLeaveBalance();
        entry.setCompany(employee.getCompany());
        entry.setBatch(batch);
        entry.setEmployee(employee);
        entry.setLeaveType(type);
        entry.setLeaveYear(p.year());
        entry.setOpeningDays(p.days());
        String outcome;
        if (options.carryForward()) {
            LeaveBalance row = leaveBalanceRepository
                    .findByEmployee_Company_IdAndEmployeeIdAndLeaveTypeIdAndYear(companyId, employee.getId(), type.getId(), p.year())
                    .orElseGet(() -> {
                        LeaveBalance created = new LeaveBalance();
                        created.setCompany(employee.getCompany());
                        created.setEmployee(employee);
                        created.setLeaveType(type);
                        created.setYear(p.year());
                        created.setAllocatedDays(0);
                        created.setCarriedForwardDays(0);
                        return created;
                    });
            double previous = row.getCarriedForwardDays();
            row.setCarriedForwardDays(p.days());
            leaveBalanceRepository.save(row);
            entry.setApplyMode(OpeningLeaveBalance.MODE_CARRY_FORWARD);
            entry.setPreviousCarriedDays(previous);
            entry.setAppliedDays(round2(p.days() - previous));
            outcome = "Carried-forward set to " + fmt(p.days()) + " day(s) (was " + fmt(previous) + ")";
        } else {
            double remaining = leaveBalanceService.compute(companyId, employee, type, p.year(), LeaveBalanceService.asOfFor(p.year())).remaining();
            double delta = round2(p.days() - remaining);
            entry.setApplyMode(OpeningLeaveBalance.MODE_ADJUSTMENT);
            entry.setAppliedDays(delta);
            if (delta != 0) {
                LeaveBalanceAdjustment adjustment = new LeaveBalanceAdjustment();
                adjustment.setCompany(employee.getCompany());
                adjustment.setEmployee(employee);
                adjustment.setLeaveType(type);
                adjustment.setLeaveYear(p.year());
                adjustment.setDays(delta);
                adjustment.setEffectiveDate(LeaveBalanceService.asOfFor(p.year()));
                adjustment.setReason(REASON + " (batch #" + batch.getId() + ")");
                currentEmployee().ifPresent(adjustment::setAdjustedBy);
                entry.setAdjustment(adjustmentRepository.save(adjustment));
            }
            outcome = "Balance set to " + fmt(p.days()) + " day(s) (" + (delta >= 0 ? "+" : "") + fmt(delta) + ")";
        }
        OpeningLeaveBalance saved = openingRepository.save(entry);
        auditLogService.log("OpeningLeaveBalance", saved.getId(), existing != null ? "REPLACE" : "IMPORT",
                REASON + ": " + p.label() + " - " + outcome);
        return outcome;
    }

    /** Undoes an earlier import of the same key: soft-deletes its adjustment or restores the old carry-forward. */
    private void reverse(Long companyId, OpeningLeaveBalance existing) {
        LocalDateTime now = LocalDateTime.now(LeaveBalanceService.IST);
        if (OpeningLeaveBalance.MODE_CARRY_FORWARD.equals(existing.getApplyMode())) {
            leaveBalanceRepository.findByEmployee_Company_IdAndEmployeeIdAndLeaveTypeIdAndYear(companyId,
                    existing.getEmployee().getId(), existing.getLeaveType().getId(), existing.getLeaveYear())
                    .ifPresent(row -> {
                        row.setCarriedForwardDays(nz(existing.getPreviousCarriedDays()));
                        leaveBalanceRepository.saveAndFlush(row);
                    });
        } else if (existing.getAdjustment() != null) {
            LeaveBalanceAdjustment adjustment = existing.getAdjustment();
            adjustment.setDeleted(true);
            adjustment.setDeletedAt(now);
            adjustmentRepository.saveAndFlush(adjustment);
        }
        existing.setDeleted(true);
        existing.setDeletedAt(now);
        // Flush now: the partial unique index allows only one live row per key, and Hibernate would otherwise
        // insert the replacement before running this update.
        openingRepository.saveAndFlush(existing);
    }

    private Optional<Employee> currentEmployee() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return Optional.empty();
        return employeeRepository.findByUser_UsernameAndDeletedFalse(auth.getName());
    }

    private static double nz(Double value) {
        return value == null ? 0 : value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    static String fmt(double value) {
        double v = round2(value);
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
