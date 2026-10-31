package com.haodaone.salary.fbp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.salary.adjustment.PayrollAdjustment;
import com.haodaone.salary.adjustment.PayrollAdjustmentProvider;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Flexible benefit plan, reimbursement model. An employee splits part of their special allowance across tax-free
 * components (each within its annual limit); once HR approves, special allowance drops by that monthly amount and each
 * processed payroll month earns that month's share. Bills claimed against what's been earned are paid tax-free with
 * payroll; in the March payroll any unclaimed balance is paid as taxable salary so nothing is lost.
 */
@Service
public class FbpService implements PayrollAdjustmentProvider {

    static final String SOURCE_CLAIM = "FBP_CLAIM";
    static final String SOURCE_YEAR_END = "FBP_YEAR_END";
    static final List<String[]> DEFAULTS = List.of(
            new String[]{"Fuel & vehicle maintenance", "Fuel, servicing and parking for a vehicle used for work", "28800"},
            new String[]{"Telephone & internet", "Mobile and broadband bills", "24000"},
            new String[]{"Meal vouchers", "Meals during working hours", "26400"},
            new String[]{"Books & periodicals", "Books, journals and online subscriptions", "12000"},
            new String[]{"Uniform", "Uniform purchase and maintenance", "10000"});

    private final FbpComponentRepository components;
    private final FbpDeclarationRepository declarations;
    private final FbpClaimRepository claims;
    private final EmployeeRepository employees;
    private final SalaryStructureRepository structures;
    private final PayrollItemRepository payrollItems;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FbpService(FbpComponentRepository components, FbpDeclarationRepository declarations, FbpClaimRepository claims,
                      EmployeeRepository employees, SalaryStructureRepository structures, PayrollItemRepository payrollItems,
                      AuthorizationService authorizationService, NotificationService notificationService,
                      AuditLogService auditLogService, ObjectMapper objectMapper, Clock applicationClock) {
        this.components = components;
        this.declarations = declarations;
        this.claims = claims;
        this.employees = employees;
        this.structures = structures;
        this.payrollItems = payrollItems;
        this.authorizationService = authorizationService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = applicationClock;
    }

    public record ComponentDTO(Long id, String name, String description, BigDecimal annualLimit, boolean active) { }
    public record Line(Long componentId, BigDecimal annualAmount) { }
    public record LineView(Long componentId, String name, BigDecimal annualAmount, BigDecimal monthlyAmount, BigDecimal earned,
                           BigDecimal claimed, BigDecimal available) { }
    public record ClaimDTO(Long id, Long employeeId, String employeeName, String employeeCode, Long componentId, String componentName,
                           LocalDate billDate, String billReference, String description, BigDecimal amount, String status,
                           String reviewNote, boolean paid) { }
    public record DeclarationDTO(Long id, Long employeeId, String employeeName, String employeeCode, int financialYear, String status,
                                 BigDecimal monthlyTotal, String startMonth, String reviewNote, List<LineView> lines,
                                 int monthsEarned, boolean yearEndPaid) { }
    public record MyView(int financialYear, BigDecimal monthlySpecialAllowance, List<ComponentDTO> components,
                         DeclarationDTO declaration, List<ClaimDTO> claims) { }
    public record DeclareBody(Integer financialYear, List<Line> lines) { }
    public record ClaimBody(Long componentId, LocalDate billDate, String billReference, String description, BigDecimal amount) { }

    // ------------------------------------------------------------------ components (HR)

    @Transactional(readOnly = true)
    public List<ComponentDTO> listComponents() {
        return components.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(hrCompany()).stream().map(FbpService::toDto).toList();
    }

    @Transactional
    public List<ComponentDTO> seedDefaults() {
        Long companyId = hrCompany();
        Set<String> existing = components.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(c -> c.getName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        for (String[] d : DEFAULTS) {
            if (existing.contains(d[0].toLowerCase(Locale.ROOT))) continue;
            FbpComponent c = new FbpComponent();
            c.setCompanyId(companyId);
            c.setName(d[0]);
            c.setDescription(d[1]);
            c.setAnnualLimit(new BigDecimal(d[2]));
            components.save(c);
        }
        auditLogService.log("FBP", companyId, "COMPONENTS_SEEDED", "Default FBP components added");
        return listComponents();
    }

    @Transactional
    public ComponentDTO saveComponent(Long id, ComponentDTO body) {
        Long companyId = hrCompany();
        FbpComponent c = id == null ? new FbpComponent()
                : components.findByIdAndCompanyIdAndDeletedFalse(id, companyId).orElseThrow(() -> new ResourceNotFoundException("Component not found"));
        if (id == null) c.setCompanyId(companyId);
        if (body.name() == null || body.name().isBlank() || body.name().length() > 80) throw new BadRequestException("Give the component a name (up to 80 characters)");
        if (body.annualLimit() == null || body.annualLimit().signum() <= 0) throw new BadRequestException("Set a yearly limit above zero");
        c.setName(body.name().trim());
        c.setDescription(body.description() == null || body.description().isBlank() ? null : body.description().trim());
        c.setAnnualLimit(body.annualLimit().setScale(2, RoundingMode.HALF_UP));
        c.setActive(id == null || body.active());
        c = components.save(c);
        auditLogService.log("FBP", c.getId(), id == null ? "COMPONENT_ADDED" : "COMPONENT_UPDATED", c.getName());
        return toDto(c);
    }

    // ------------------------------------------------------------------ employee

    @Transactional(readOnly = true)
    public MyView mine(Integer fy) {
        Employee me = me();
        int year = fy == null ? currentFy() : fy;
        Long companyId = me.getCompany().getId();
        BigDecimal special = structures.findByEmployeeIdAndActiveTrueAndDeletedFalse(me.getId())
                .map(s -> s.getComponents().getSpecialAllowance()).orElse(BigDecimal.ZERO);
        FbpDeclaration d = declarations.findAllByEmployeeIdAndDeletedFalseOrderByFinancialYearDescIdDesc(me.getId()).stream()
                .filter(x -> x.getFinancialYear() == year && !FbpDeclaration.REJECTED.equals(x.getStatus())).findFirst().orElse(null);
        List<ClaimDTO> myClaims = d == null ? List.of() : claims.findAllByDeclarationIdAndDeletedFalseOrderByBillDateDesc(d.getId()).stream()
                .map(c -> toDto(c, me)).toList();
        return new MyView(year, special, components.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .filter(FbpComponent::isActive).map(FbpService::toDto).toList(), d == null ? null : toDto(d, me), myClaims);
    }

    @Transactional
    public MyView declare(DeclareBody body) {
        Employee me = me();
        int fy = body == null || body.financialYear() == null ? currentFy() : body.financialYear();
        if (fy < currentFy()) throw new BadRequestException("You can only declare for the current or next financial year");
        boolean open = declarations.findAllByEmployeeIdAndDeletedFalseOrderByFinancialYearDescIdDesc(me.getId()).stream()
                .anyMatch(x -> x.getFinancialYear() == fy && !FbpDeclaration.REJECTED.equals(x.getStatus()));
        if (open) throw new BadRequestException("You've already declared for this year. Ask HR if it needs to change.");
        SalaryStructure structure = structures.findByEmployeeIdAndActiveTrueAndDeletedFalse(me.getId())
                .orElseThrow(() -> new BadRequestException("Your salary isn't set up yet"));
        List<Line> lines = validateLines(me.getCompany().getId(), body == null ? null : body.lines());
        BigDecimal monthly = monthlyTotal(lines);
        BigDecimal special = structure.getComponents().getSpecialAllowance() == null ? BigDecimal.ZERO : structure.getComponents().getSpecialAllowance();
        if (monthly.compareTo(special) > 0) {
            throw new BadRequestException("Your FBP comes out of special allowance (Rs " + special.toPlainString() + " a month); you've chosen Rs "
                    + monthly.toPlainString() + " a month");
        }
        FbpDeclaration d = new FbpDeclaration();
        d.setCompanyId(me.getCompany().getId());
        d.setEmployeeId(me.getId());
        d.setFinancialYear(fy);
        d.setStatus(FbpDeclaration.SUBMITTED);
        d.setMonthlyTotal(monthly);
        d.setLinesJson(write(lines));
        d = declarations.save(d);
        auditLogService.log("FBP_DECLARATION", d.getId(), "SUBMITTED", me.getFullName() + " declared Rs " + monthly + "/month FBP");
        notificationService.notifyPermissionRecipients(me.getCompany().getId(), me.getId(), "SALARY_MANAGE", "FBP", "FBP declaration to review",
                me.getFullName() + " chose Rs " + monthly.toPlainString() + " a month of flexible benefits.", "FBP_DECLARATION", d.getId());
        return mine(fy);
    }

    @Transactional
    public MyView claim(ClaimBody body) {
        Employee me = me();
        if (body == null || body.componentId() == null || body.amount() == null || body.amount().signum() <= 0 || body.billDate() == null) {
            throw new BadRequestException("Choose the benefit, bill date and amount");
        }
        if (body.billDate().isAfter(LocalDate.now(clock))) throw new BadRequestException("The bill date can't be in the future");
        int fy = fyOf(body.billDate());
        FbpDeclaration d = declarations.findAllByCompanyIdAndEmployeeIdAndStatusAndDeletedFalse(me.getCompany().getId(), me.getId(), FbpDeclaration.APPROVED)
                .stream().filter(x -> x.getFinancialYear() == fy).findFirst()
                .orElseThrow(() -> new BadRequestException("You don't have an approved FBP for the year of this bill"));
        if (d.isYearEndSettled()) throw new BadRequestException("This year's FBP has been closed and the balance paid out");
        LineView line = toDto(d, me).lines().stream().filter(l -> l.componentId().equals(body.componentId())).findFirst()
                .orElseThrow(() -> new BadRequestException("That benefit isn't in your FBP for this year"));
        if (body.amount().compareTo(line.available()) > 0) {
            throw new BadRequestException("You can claim up to Rs " + line.available().toPlainString() + " for " + line.name()
                    + " so far - it builds up each month as salary is processed");
        }
        FbpClaim c = new FbpClaim();
        c.setCompanyId(d.getCompanyId());
        c.setEmployeeId(me.getId());
        c.setDeclarationId(d.getId());
        c.setComponentId(body.componentId());
        c.setBillDate(body.billDate());
        c.setBillReference(trim(body.billReference(), 100));
        c.setDescription(trim(body.description(), 500));
        c.setAmount(body.amount().setScale(2, RoundingMode.HALF_UP));
        c.setStatus(FbpClaim.SUBMITTED);
        c = claims.save(c);
        notificationService.notifyPermissionRecipients(d.getCompanyId(), me.getId(), "SALARY_MANAGE", "FBP", "FBP claim to review",
                me.getFullName() + " claimed Rs " + c.getAmount().toPlainString() + " for " + line.name() + ".", "FBP_CLAIM", c.getId());
        return mine(fy);
    }

    // ------------------------------------------------------------------ HR review

    @Transactional(readOnly = true)
    public List<DeclarationDTO> declarations(String status) {
        Long companyId = hrCompany();
        List<FbpDeclaration> rows = declarations.findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(companyId).stream()
                .filter(d -> status == null || status.isBlank() || status.equalsIgnoreCase(d.getStatus())).toList();
        Map<Long, Employee> people = people(companyId, rows.stream().map(FbpDeclaration::getEmployeeId).collect(Collectors.toSet()));
        return rows.stream().filter(d -> people.containsKey(d.getEmployeeId())).map(d -> toDto(d, people.get(d.getEmployeeId()))).toList();
    }

    @Transactional
    public DeclarationDTO reviewDeclaration(Long id, boolean approve, String note, String actor) {
        Long companyId = hrCompany();
        FbpDeclaration d = declarations.findByIdAndCompanyIdAndDeletedFalse(id, companyId).orElseThrow(() -> new ResourceNotFoundException("Declaration not found"));
        if (!FbpDeclaration.SUBMITTED.equals(d.getStatus())) throw new BadRequestException("This declaration has already been reviewed");
        Employee e = employees.findByIdAndCompany_IdAndDeletedFalse(d.getEmployeeId(), companyId).orElseThrow();
        if (!approve && (note == null || note.isBlank())) throw new BadRequestException("Add a note so the employee knows what to change");
        d.setReviewedBy(actor);
        d.setReviewNote(trim(note, 500));
        if (approve) {
            SalaryStructure current = structures.findByEmployeeIdAndActiveTrueAndDeletedFalse(e.getId())
                    .orElseThrow(() -> new BadRequestException(e.getFullName() + " has no salary structure"));
            BigDecimal special = current.getComponents().getSpecialAllowance() == null ? BigDecimal.ZERO : current.getComponents().getSpecialAllowance();
            if (d.getMonthlyTotal().compareTo(special) > 0) throw new BadRequestException("Special allowance is now lower than the FBP chosen - ask the employee to redo it");
            carveOut(current, d.getMonthlyTotal(), d.getFinancialYear());
            YearMonth fyStart = YearMonth.of(d.getFinancialYear(), 4);
            YearMonth now = YearMonth.now(clock);
            d.setStartMonth((now.isAfter(fyStart) ? now : fyStart).toString());
            d.setStatus(FbpDeclaration.APPROVED);
        } else {
            d.setStatus(FbpDeclaration.REJECTED);
        }
        declarations.save(d);
        auditLogService.log("FBP_DECLARATION", d.getId(), approve ? "APPROVED" : "REJECTED", actor + (approve ? " approved " : " sent back ") + e.getFullName() + "'s FBP");
        if (e.getUser() != null) {
            notificationService.notifyUser(companyId, e.getUser().getId(), "FBP", approve ? "FBP approved" : "FBP needs changes",
                    approve ? "Your flexible benefits start with " + d.getStartMonth() + " payroll. Claim bills as you spend."
                            : "Your FBP declaration was sent back: " + note, "FBP_DECLARATION", d.getId(), "NORMAL");
        }
        return toDto(d, e);
    }

    @Transactional(readOnly = true)
    public List<ClaimDTO> claims(String status) {
        Long companyId = hrCompany();
        List<FbpClaim> rows = claims.findAllByCompanyIdAndStatusAndDeletedFalseOrderByCreatedAtAsc(companyId, status == null ? FbpClaim.SUBMITTED : status.toUpperCase(Locale.ROOT));
        Map<Long, Employee> people = people(companyId, rows.stream().map(FbpClaim::getEmployeeId).collect(Collectors.toSet()));
        return rows.stream().filter(c -> people.containsKey(c.getEmployeeId())).map(c -> toDto(c, people.get(c.getEmployeeId()))).toList();
    }

    @Transactional
    public ClaimDTO reviewClaim(Long id, boolean approve, String note, String actor) {
        Long companyId = hrCompany();
        FbpClaim c = claims.findByIdAndCompanyIdAndDeletedFalse(id, companyId).orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
        if (!FbpClaim.SUBMITTED.equals(c.getStatus())) throw new BadRequestException("This claim has already been reviewed");
        if (!approve && (note == null || note.isBlank())) throw new BadRequestException("Add a note so the employee knows why");
        c.setStatus(approve ? FbpClaim.APPROVED : FbpClaim.REJECTED);
        c.setReviewedBy(actor);
        c.setReviewNote(trim(note, 500));
        claims.save(c);
        Employee e = employees.findByIdAndCompany_IdAndDeletedFalse(c.getEmployeeId(), companyId).orElseThrow();
        auditLogService.log("FBP_CLAIM", c.getId(), approve ? "APPROVED" : "REJECTED", actor + (approve ? " approved " : " rejected ") + "Rs " + c.getAmount());
        if (e.getUser() != null) {
            notificationService.notifyUser(companyId, e.getUser().getId(), "FBP", approve ? "FBP claim approved" : "FBP claim rejected",
                    approve ? "Rs " + c.getAmount().toPlainString() + " will be paid tax-free with your next salary." : "Your claim was rejected: " + note,
                    "FBP_CLAIM", c.getId(), "NORMAL");
        }
        return toDto(c, e);
    }

    // ------------------------------------------------------------------ payroll hook

    @Override
    public Set<String> sourceTypes() {
        return Set.of(SOURCE_CLAIM, SOURCE_YEAR_END);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollAdjustment> adjustmentsFor(Long companyId, Long employeeId, YearMonth period) {
        List<PayrollAdjustment> out = new ArrayList<>();
        Map<Long, String> names = new HashMap<>();
        components.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).forEach(c -> names.put(c.getId(), c.getName()));
        BigDecimal paidNow = BigDecimal.ZERO;
        for (FbpClaim c : claims.findAllByCompanyIdAndEmployeeIdAndStatusAndSettledFalseAndDeletedFalse(companyId, employeeId, FbpClaim.APPROVED)) {
            out.add(new PayrollAdjustment("FBP", "FBP: " + names.getOrDefault(c.getComponentId(), "reimbursement"), c.getAmount(), false, SOURCE_CLAIM, c.getId()));
            paidNow = paidNow.add(c.getAmount());
        }
        if (period.getMonthValue() == 3) {
            int fy = period.getYear() - 1;
            Employee e = employees.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId).orElse(null);
            for (FbpDeclaration d : declarations.findAllByCompanyIdAndEmployeeIdAndStatusAndDeletedFalse(companyId, employeeId, FbpDeclaration.APPROVED)) {
                if (d.getFinancialYear() != fy || d.isYearEndSettled() || e == null) continue;
                // Earned through this March (the current run is about to be processed) minus every approved claim.
                BigDecimal earned = d.getMonthlyTotal().multiply(BigDecimal.valueOf(monthsEarned(d) + 1L));
                BigDecimal claimed = claims.findAllByDeclarationIdAndDeletedFalseOrderByBillDateDesc(d.getId()).stream()
                        .filter(c -> FbpClaim.APPROVED.equals(c.getStatus())).map(FbpClaim::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal unclaimed = earned.subtract(claimed).setScale(2, RoundingMode.HALF_UP);
                if (unclaimed.signum() > 0) {
                    out.add(new PayrollAdjustment("FBP_BALANCE", "FBP unclaimed balance (taxable)", unclaimed, true, SOURCE_YEAR_END, d.getId()));
                }
            }
        }
        return out;
    }

    @Override
    @Transactional
    public void markSettled(List<PayrollAdjustment> adjustments, Long payrollItemId) {
        for (PayrollAdjustment a : adjustments) {
            if (SOURCE_CLAIM.equals(a.sourceType())) {
                claims.findById(a.sourceId()).ifPresent(c -> {
                    c.setSettled(true);
                    c.setPayrollItemId(payrollItemId);
                    claims.save(c);
                });
            } else if (SOURCE_YEAR_END.equals(a.sourceType())) {
                declarations.findById(a.sourceId()).ifPresent(d -> {
                    d.setYearEndSettled(true);
                    declarations.save(d);
                });
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** New active structure with special allowance lowered by the FBP amount (FBP is paid back through claims). */
    private void carveOut(SalaryStructure current, BigDecimal monthly, int fy) {
        current.setActive(false);
        structures.save(current);
        structures.flush();
        SalaryComponents c = copy(current.getComponents());
        c.setSpecialAllowance(c.getSpecialAllowance().subtract(monthly));
        SalaryStructure next = new SalaryStructure();
        next.setCompany(current.getCompany());
        next.setEmployee(current.getEmployee());
        next.setEffectiveFrom(LocalDate.now(clock));
        next.setActive(true);
        next.setComponents(c);
        next.setAutoStatutory(current.isAutoStatutory());
        next.setNotes("FBP FY " + fy + "-" + String.valueOf(fy + 1).substring(2) + ": Rs " + monthly.toPlainString() + "/month moved to flexible benefits");
        next.recalculate();
        structures.save(next);
    }

    private static SalaryComponents copy(SalaryComponents s) {
        SalaryComponents c = new SalaryComponents();
        c.setBasicSalary(s.getBasicSalary());
        c.setHra(s.getHra());
        c.setSpecialAllowance(s.getSpecialAllowance() == null ? BigDecimal.ZERO : s.getSpecialAllowance());
        c.setMedicalAllowance(s.getMedicalAllowance());
        c.setTravelAllowance(s.getTravelAllowance());
        c.setBonus(s.getBonus());
        c.setIncentives(s.getIncentives());
        c.setOvertime(s.getOvertime());
        c.setPf(s.getPf());
        c.setEsi(s.getEsi());
        c.setProfessionalTax(s.getProfessionalTax());
        c.setTds(s.getTds());
        c.setOtherDeductions(s.getOtherDeductions());
        return c;
    }

    /** Processed payroll months in the declaration's year from its start month. */
    int monthsEarned(FbpDeclaration d) {
        if (d.getStartMonth() == null) return 0;
        String start = d.getStartMonth();
        return (int) payrollItems.findFinalizedForEmployeeInFinancialYear(d.getCompanyId(), d.getEmployeeId(), d.getFinancialYear(), d.getFinancialYear() + 1)
                .stream().map(i -> YearMonth.of(i.getPayrollRun().getPeriodYear(), i.getPayrollRun().getPeriodMonth()).toString())
                .filter(m -> m.compareTo(start) >= 0).distinct().count();
    }

    private List<Line> validateLines(Long companyId, List<Line> lines) {
        if (lines == null) throw new BadRequestException("Choose at least one benefit");
        Map<Long, FbpComponent> byId = components.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .filter(FbpComponent::isActive).collect(Collectors.toMap(FbpComponent::getId, c -> c));
        List<Line> clean = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Line l : lines) {
            if (l == null || l.annualAmount() == null || l.annualAmount().signum() <= 0) continue;
            FbpComponent c = byId.get(l.componentId());
            if (c == null) throw new BadRequestException("Unknown benefit");
            if (!seen.add(c.getId())) throw new BadRequestException(c.getName() + " is listed twice");
            if (l.annualAmount().compareTo(c.getAnnualLimit()) > 0) {
                throw new BadRequestException(c.getName() + " is limited to Rs " + c.getAnnualLimit().toPlainString() + " a year");
            }
            clean.add(new Line(c.getId(), l.annualAmount().setScale(2, RoundingMode.HALF_UP)));
        }
        if (clean.isEmpty()) throw new BadRequestException("Choose at least one benefit");
        return clean;
    }

    private static BigDecimal monthlyTotal(List<Line> lines) {
        return lines.stream().map(l -> l.annualAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private DeclarationDTO toDto(FbpDeclaration d, Employee e) {
        Map<Long, String> names = new HashMap<>();
        components.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(d.getCompanyId()).forEach(c -> names.put(c.getId(), c.getName()));
        int months = FbpDeclaration.APPROVED.equals(d.getStatus()) ? monthsEarned(d) : 0;
        Map<Long, BigDecimal> claimedBy = new HashMap<>();
        claims.findAllByDeclarationIdAndDeletedFalseOrderByBillDateDesc(d.getId()).stream()
                .filter(c -> !FbpClaim.REJECTED.equals(c.getStatus()))
                .forEach(c -> claimedBy.merge(c.getComponentId(), c.getAmount(), BigDecimal::add));
        List<LineView> views = read(d.getLinesJson()).stream().map(l -> {
            BigDecimal monthly = l.annualAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            BigDecimal earned = monthly.multiply(BigDecimal.valueOf(months));
            BigDecimal claimed = claimedBy.getOrDefault(l.componentId(), BigDecimal.ZERO);
            return new LineView(l.componentId(), names.getOrDefault(l.componentId(), "Benefit"), l.annualAmount(), monthly, earned, claimed,
                    earned.subtract(claimed).max(BigDecimal.ZERO));
        }).toList();
        return new DeclarationDTO(d.getId(), e.getId(), e.getFullName(), e.getEmployeeCode(), d.getFinancialYear(), d.getStatus(),
                d.getMonthlyTotal(), d.getStartMonth(), d.getReviewNote(), views, months, d.isYearEndSettled());
    }

    private ClaimDTO toDto(FbpClaim c, Employee e) {
        String name = components.findById(c.getComponentId()).map(FbpComponent::getName).orElse("Benefit");
        return new ClaimDTO(c.getId(), e.getId(), e.getFullName(), e.getEmployeeCode(), c.getComponentId(), name, c.getBillDate(),
                c.getBillReference(), c.getDescription(), c.getAmount(), c.getStatus(), c.getReviewNote(), c.isSettled());
    }

    private static ComponentDTO toDto(FbpComponent c) {
        return new ComponentDTO(c.getId(), c.getName(), c.getDescription(), c.getAnnualLimit(), c.isActive());
    }

    private Map<Long, Employee> people(Long companyId, Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return employees.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(companyId, ids).stream().collect(Collectors.toMap(Employee::getId, e -> e));
    }

    private String write(List<Line> lines) {
        try {
            return objectMapper.writeValueAsString(lines);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private List<Line> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Line>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return List.of();
        }
    }

    private static String trim(String v, int max) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        if (t.length() > max) throw new BadRequestException("Keep it under " + max + " characters");
        return t;
    }

    private int currentFy() {
        return fyOf(LocalDate.now(clock));
    }

    static int fyOf(LocalDate date) {
        return date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
    }

    private Employee me() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserPrincipal principal)) throw new BadRequestException("Sign in again");
        Long companyId = TenantContext.getCurrentTenant();
        return employees.findByUser_IdAndDeletedFalse(principal.getId())
                .filter(e -> e.getCompany() != null && e.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new BadRequestException("Your login isn't linked to an employee record"));
    }

    private Long hrCompany() {
        if (!authorizationService.hasOrganizationScope("SALARY_MANAGE")) throw new AccessDeniedException("SALARY_MANAGE requires ORGANIZATION scope.");
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        return companyId;
    }
}
