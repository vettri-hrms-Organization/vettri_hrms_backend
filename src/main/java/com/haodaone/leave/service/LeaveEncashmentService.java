package com.haodaone.leave.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.dto.LeavePolicyDTOs.EncashmentApplyRequest;
import com.haodaone.leave.dto.LeavePolicyDTOs.EncashmentDTO;
import com.haodaone.leave.dto.LeavePolicyDTOs.EncashmentPayable;
import com.haodaone.leave.dto.LeavePolicyDTOs.FinalSettlementLine;
import com.haodaone.leave.dto.LeavePolicyDTOs.FinalSettlementPreview;
import com.haodaone.leave.entity.LeaveEncashment;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.LeaveEncashmentRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Leave encashment. Employee requests N days of an encashable type -> HR (LEAVE_MANAGE, org scope) approves ->
 * the days are consumed from the balance and the row becomes a payable with a computed amount.
 *
 * <p><b>Rate:</b> per-day = monthly BASIC (components.basicSalary) or GROSS (grossSalary) of the employee's
 * active salary structure / 30, rounded HALF_UP to paise; amount = per-day x days. 30 is the fixed divisor
 * commonly used for encashment in India regardless of the month's length.
 *
 * <p><b>Payroll hook:</b> {@link #approvedUnpaidEncashments} / {@link #markPaid} - the payroll run adds the
 * amounts as an earning and marks the rows PAID with its payroll item id.
 */
@Service
public class LeaveEncashmentService {

    public static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30);

    private final LeaveEncashmentRepository encashmentRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final SalaryStructureRepository salaryStructureRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final AuditLogService auditLogService;
    private NotificationService notificationService;

    public LeaveEncashmentService(LeaveEncashmentRepository encashmentRepository, LeaveTypeRepository leaveTypeRepository,
                                  LeaveRequestRepository leaveRequestRepository, EmployeeRepository employeeRepository,
                                  SalaryStructureRepository salaryStructureRepository, LeaveBalanceService leaveBalanceService,
                                  AuditLogService auditLogService) {
        this.encashmentRepository = encashmentRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.salaryStructureRepository = salaryStructureRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.auditLogService = auditLogService;
    }

    @Autowired
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Per-day encashment rate: monthly basis amount / 30, HALF_UP to 2 decimals. */
    public static BigDecimal perDayRate(BigDecimal monthlyAmount) {
        if (monthlyAmount == null || monthlyAmount.signum() <= 0) return BigDecimal.ZERO.setScale(2);
        return monthlyAmount.divide(DAYS_PER_MONTH, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal amountFor(BigDecimal perDayRate, double days) {
        return perDayRate.multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP);
    }

    static String normalizeBasis(String basis) {
        return "GROSS".equalsIgnoreCase(basis) ? "GROSS" : "BASIC";
    }

    private Optional<BigDecimal> monthlyBasis(Long companyId, Long employeeId, String basis) {
        return salaryStructureRepository.findByEmployee_Company_IdAndEmployeeIdAndActiveTrueAndDeletedFalse(companyId, employeeId)
                .map(structure -> monthlyAmount(structure, basis));
    }

    private static BigDecimal monthlyAmount(SalaryStructure structure, String basis) {
        if ("GROSS".equals(basis)) return structure.getGrossSalary();
        return structure.getComponents() != null ? structure.getComponents().getBasicSalary() : BigDecimal.ZERO;
    }

    // ------------------------------------------------------------------ request / decide

    @Transactional
    public EncashmentDTO request(EncashmentApplyRequest request) {
        Long companyId = LeaveBalanceService.requiredTenant();
        if (request == null || request.getEmployeeId() == null || request.getLeaveTypeId() == null) {
            throw new BadRequestException("Employee and leave type are required");
        }
        double days = request.getDays() == null ? 0 : request.getDays();
        if (days <= 0 || days * 2 != Math.rint(days * 2)) {
            throw new BadRequestException("Days to encash must be a positive multiple of 0.5");
        }
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(request.getEmployeeId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        LeaveType type = leaveTypeRepository.findByIdAndCompany_IdAndDeletedFalse(request.getLeaveTypeId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));
        if (!type.isEncashable() || type.isSystemManaged()) {
            throw new BadRequestException(type.getName() + " cannot be encashed");
        }
        int year = LeaveBalanceService.today().getYear();
        employeeRepository.lockById(employee.getId());
        validateAvailability(companyId, employee, type, year, days, true);

        LeaveEncashment entity = new LeaveEncashment();
        entity.setCompany(employee.getCompany());
        entity.setEmployee(employee);
        entity.setLeaveType(type);
        entity.setLeaveYear(year);
        entity.setDays(days);
        entity.setReason(trim(request.getReason()));
        entity.setStatus("PENDING");
        entity.setBasis(normalizeBasis(type.getEncashmentBasis()));
        LeaveEncashment saved = encashmentRepository.save(entity);

        auditLogService.log("LeaveEncashment", saved.getId(), "CREATE", String.format(Locale.ROOT,
                "%s requested encashment of %s day(s) of %s", employee.getFullName(), LeaveBalanceService.fmt(days), type.getName()));
        if (notificationService != null) {
            notificationService.notifyPermissionRecipients(companyId, employee.getId(), "LEAVE_MANAGE",
                    "LEAVE_ENCASHMENT_SUBMITTED", "Leave encashment needs review",
                    employee.getFullName() + " requested encashment of " + LeaveBalanceService.fmt(days) + " day(s) of " + type.getName() + ".",
                    "LeaveEncashment", saved.getId());
        }
        return EncashmentDTO.from(saved);
    }

    /** Checks the yearly encashment limit and the balance (net of pending leave and, when requesting, pending encashments). */
    private void validateAvailability(Long companyId, Employee employee, LeaveType type, int year, double days, boolean includePendingEncashments) {
        double consumed = encashmentRepository.sumConsumedDays(companyId, employee.getId(), type.getId(), year);
        double pendingEncash = includePendingEncashments ? encashmentRepository.sumPendingDays(companyId, employee.getId(), type.getId(), year) : 0;
        Double max = type.getMaxEncashableDaysPerYear();
        if (max != null && consumed + pendingEncash + days > max + 1e-9) {
            throw new BadRequestException(String.format(Locale.ROOT,
                    "At most %s day(s) of %s can be encashed per year (%s already encashed or requested)",
                    LeaveBalanceService.fmt(max), type.getName(), LeaveBalanceService.fmt(consumed + pendingEncash)));
        }
        LeaveBalanceService.BalanceDetail detail = leaveBalanceService.compute(companyId, employee, type, year, LeaveBalanceService.asOfFor(year));
        double pendingLeave = leaveRequestRepository.sumPendingDays(companyId, employee.getId(), type.getId(), year);
        double available = detail.remaining() - pendingLeave - pendingEncash;
        if (days > available + 1e-9) {
            throw new BadRequestException(String.format(Locale.ROOT,
                    "Only %s day(s) of %s are available to encash", LeaveBalanceService.fmt(Math.max(available, 0)), type.getName()));
        }
    }

    @Transactional
    public EncashmentDTO decide(Long id, boolean approve, String note) {
        Long companyId = LeaveBalanceService.requiredTenant();
        LeaveEncashment encashment = encashmentRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Encashment request not found: " + id));
        if (!"PENDING".equals(encashment.getStatus())) {
            throw new BadRequestException("Only pending requests can be approved or rejected (current status: " + encashment.getStatus() + ")");
        }
        Employee employee = encashment.getEmployee();
        boolean own = leaveBalanceService.currentEmployee().map(me -> me.getId().equals(employee.getId())).orElse(false);
        if (own) {
            throw new AccessDeniedException("You can't approve or reject your own encashment request.");
        }
        if (approve) {
            employeeRepository.lockById(employee.getId());
            validateAvailability(companyId, employee, encashment.getLeaveType(), encashment.getLeaveYear(), encashment.getDays(), false);
            String basis = normalizeBasis(encashment.getLeaveType().getEncashmentBasis());
            BigDecimal monthly = monthlyBasis(companyId, employee.getId(), basis)
                    .orElseThrow(() -> new BadRequestException(employee.getFullName() + " has no active salary structure - set one up before approving encashment"));
            BigDecimal rate = perDayRate(monthly);
            encashment.setBasis(basis);
            encashment.setPerDayRate(rate);
            encashment.setAmount(amountFor(rate, encashment.getDays()));
        }
        encashment.setStatus(approve ? "APPROVED" : "REJECTED");
        encashment.setDecidedAt(LocalDateTime.now(LeaveBalanceService.IST));
        encashment.setDecisionNote(trim(note));
        leaveBalanceService.currentEmployee().ifPresent(encashment::setDecidedBy);
        LeaveEncashment saved = encashmentRepository.save(encashment);

        auditLogService.log("LeaveEncashment", saved.getId(), approve ? "APPROVE" : "REJECT",
                approve ? String.format(Locale.ROOT, "Approved %s day(s) of %s: %s x %s/day = INR %s (%s basis)",
                        LeaveBalanceService.fmt(saved.getDays()), saved.getLeaveType().getName(), LeaveBalanceService.fmt(saved.getDays()),
                        saved.getPerDayRate().toPlainString(), saved.getAmount().toPlainString(), saved.getBasis())
                        : "Rejected" + (note != null && !note.isBlank() ? ": " + note : ""));
        if (notificationService != null && employee.getUser() != null) {
            notificationService.notifyUser(companyId, employee.getUser().getId(), "LEAVE_ENCASHMENT_DECISION",
                    approve ? "Leave encashment approved" : "Leave encashment declined",
                    approve ? "Encashment of " + LeaveBalanceService.fmt(saved.getDays()) + " day(s) was approved (INR "
                            + saved.getAmount().toPlainString() + "); it will be paid with payroll."
                            : "Your leave encashment request was declined.",
                    "LeaveEncashment", saved.getId(), "NORMAL");
        }
        return EncashmentDTO.from(saved);
    }

    @Transactional
    public EncashmentDTO cancel(Long id) {
        Long companyId = LeaveBalanceService.requiredTenant();
        LeaveEncashment encashment = encashmentRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Encashment request not found: " + id));
        boolean owner = leaveBalanceService.currentEmployee().map(me -> me.getId().equals(encashment.getEmployee().getId())).orElse(false);
        if (!owner) throw new AccessDeniedException("Only the requester can cancel this encashment request.");
        if (!"PENDING".equals(encashment.getStatus())) {
            throw new BadRequestException("Only pending encashment requests can be cancelled");
        }
        encashment.setStatus("CANCELLED");
        LeaveEncashment saved = encashmentRepository.save(encashment);
        auditLogService.log("LeaveEncashment", saved.getId(), "CANCEL", "Encashment request cancelled");
        return EncashmentDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public List<EncashmentDTO> listForEmployee(Long employeeId) {
        Long companyId = LeaveBalanceService.requiredTenant();
        return encashmentRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByCreatedAtDesc(companyId, employeeId)
                .stream().map(EncashmentDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EncashmentDTO> listAll(String status) {
        Long companyId = LeaveBalanceService.requiredTenant();
        String wanted = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return encashmentRepository.findAllByCompany_IdAndDeletedFalseOrderByCreatedAtDesc(companyId).stream()
                .filter(e -> wanted.isEmpty() || wanted.equals(e.getStatus()))
                .map(EncashmentDTO::from).toList();
    }

    // ------------------------------------------------------------------ payroll hook

    /**
     * Approved encashments not yet attached to a payroll item, approved on or before the end of {@code period}
     * (earlier unpaid ones are included so nothing is skipped).
     */
    @Transactional(readOnly = true)
    public List<EncashmentPayable> approvedUnpaidEncashments(Long companyId, Long employeeId, YearMonth period) {
        LocalDateTime before = period.plusMonths(1).atDay(1).atStartOfDay();
        return encashmentRepository.findApprovedUnpaid(companyId, employeeId, before).stream()
                .map(e -> new EncashmentPayable(e.getId(), e.getEmployee().getId(), e.getLeaveType().getId(),
                        e.getLeaveType().getCode(), e.getLeaveType().getName(), e.getLeaveYear(), e.getDays(),
                        e.getPerDayRate(), e.getAmount(), e.getBasis(), e.getDecidedAt()))
                .toList();
    }

    /** Marks APPROVED encashments PAID against a payroll item. Idempotent; rows of another company are ignored when a tenant is set. */
    @Transactional
    public void markPaid(List<Long> ids, Long payrollItemId) {
        if (ids == null || ids.isEmpty()) return;
        Long tenant = TenantContext.getCurrentTenant();
        LocalDateTime now = LocalDateTime.now(LeaveBalanceService.IST);
        for (LeaveEncashment encashment : encashmentRepository.findAllByIdInAndDeletedFalse(ids)) {
            if (tenant != null && (encashment.getCompany() == null || !tenant.equals(encashment.getCompany().getId()))) continue;
            if ("PAID".equals(encashment.getStatus())) continue;
            if (!"APPROVED".equals(encashment.getStatus())) continue;
            encashment.setStatus("PAID");
            encashment.setPayrollItemId(payrollItemId);
            encashment.setPaidAt(now);
            encashmentRepository.save(encashment);
            auditLogService.log("LeaveEncashment", encashment.getId(), "PAID", "Paid via payroll item " + payrollItemId);
        }
    }

    // ------------------------------------------------------------------ final settlement

    /**
     * Exit preview: remaining days of every encashable type (EL when none is flagged) as of the last working
     * date (or today) x per-day rate. The yearly encashment limit does not apply at exit. Read-only.
     */
    @Transactional(readOnly = true)
    public FinalSettlementPreview finalSettlementPreview(Long employeeId) {
        Long companyId = LeaveBalanceService.requiredTenant();
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        LocalDate today = LeaveBalanceService.today();
        LocalDate asOf = employee.getLastWorkingDate() != null ? employee.getLastWorkingDate() : today;
        List<LeaveType> types = leaveBalanceService.activeTypes(companyId).stream()
                .filter(t -> t.isEncashable() && !t.isSystemManaged()).toList();
        if (types.isEmpty()) {
            types = leaveBalanceService.activeTypes(companyId).stream()
                    .filter(t -> "EL".equalsIgnoreCase(t.getCode()) && !t.isSystemManaged()).toList();
        }
        List<FinalSettlementLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean salaryFound = salaryStructureRepository
                .findByEmployee_Company_IdAndEmployeeIdAndActiveTrueAndDeletedFalse(companyId, employeeId).isPresent();
        for (LeaveType type : types) {
            LeaveBalanceService.BalanceDetail detail = leaveBalanceService.compute(companyId, employee, type, asOf.getYear(), asOf);
            double pendingEncash = encashmentRepository.sumPendingDays(companyId, employeeId, type.getId(), asOf.getYear());
            double encashable = Math.max(0, detail.remaining() - pendingEncash);
            String basis = normalizeBasis(type.getEncashmentBasis());
            BigDecimal rate = monthlyBasis(companyId, employeeId, basis).map(LeaveEncashmentService::perDayRate).orElse(BigDecimal.ZERO.setScale(2));
            BigDecimal amount = amountFor(rate, encashable);
            total = total.add(amount);
            lines.add(new FinalSettlementLine(type.getId(), type.getName(), basis, detail.remaining(), encashable, rate, amount));
        }
        String note = salaryFound
                ? "Per-day rate = monthly " + (types.isEmpty() ? "BASIC" : "basis") + " / 30. Pending encashment requests are excluded."
                : "No active salary structure - amounts shown as zero.";
        return new FinalSettlementPreview(employee.getId(), employee.getFullName(), asOf, employee.getLastWorkingDate(),
                salaryFound, lines, total.setScale(2, RoundingMode.HALF_UP), note);
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
