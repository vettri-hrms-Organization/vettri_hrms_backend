package com.haodaone.salary.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.salary.dto.TaxDeclarationDTO;
import com.haodaone.salary.dto.TaxProjectionDTO;
import com.haodaone.salary.entity.EmployeeTaxDeclaration;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.EmployeeTaxDeclarationRepository;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.salary.statutory.IncomeTaxRates.Regime;
import com.haodaone.security.AuthorizationService;
import com.haodaone.security.EmployeeSecurity;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Employee tax regime choice + investment declarations (self-service) and the
 * HR view over them. Employees may edit the current and next FY; HR
 * (SALARY_MANAGE over the employee) may edit any FY. The declaration in force
 * when a payroll run is generated drives that month's TDS.
 */
@Service
public class TaxDeclarationService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final EmployeeTaxDeclarationRepository repository;
    private final EmployeeRepository employeeRepository;
    private final SalaryStructureRepository salaryStructureRepository;
    private final StatutoryPayrollEngine engine;
    private final AuditLogService auditLogService;
    private final AuthorizationService authorizationService;
    private final EmployeeSecurity employeeSecurity;

    public TaxDeclarationService(EmployeeTaxDeclarationRepository repository, EmployeeRepository employeeRepository,
                                 SalaryStructureRepository salaryStructureRepository, StatutoryPayrollEngine engine,
                                 AuditLogService auditLogService, AuthorizationService authorizationService,
                                 EmployeeSecurity employeeSecurity) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.salaryStructureRepository = salaryStructureRepository;
        this.engine = engine;
        this.auditLogService = auditLogService;
        this.authorizationService = authorizationService;
        this.employeeSecurity = employeeSecurity;
    }

    public static int currentFinancialYear() {
        return StatutoryPayrollEngine.financialYearOf(YearMonth.now(IST));
    }

    // ----- self service -----

    @Transactional(readOnly = true)
    public TaxDeclarationDTO getMine(Integer fy) {
        Employee me = currentEmployee();
        return get(me, resolveFy(fy));
    }

    @Transactional
    public TaxDeclarationDTO saveMine(Integer fy, TaxDeclarationDTO request) {
        Employee me = currentEmployee();
        int year = resolveFy(fy);
        int current = currentFinancialYear();
        if (year < current || year > current + 1) {
            throw new BadRequestException("You can only update the declaration for " + TaxDeclarationDTO.fyLabel(current)
                    + " or " + TaxDeclarationDTO.fyLabel(current + 1) + ". Contact HR for earlier years.");
        }
        return save(me, year, request, "Employee");
    }

    @Transactional(readOnly = true)
    public TaxProjectionDTO projectMine(Integer fy) {
        return project(currentEmployee(), resolveFy(fy));
    }

    // ----- HR -----

    @Transactional(readOnly = true)
    public List<TaxDeclarationDTO> listForCompany(Integer fy) {
        if (!authorizationService.hasOrganizationScope("SALARY_VIEW")) {
            throw new AccessDeniedException("SALARY_VIEW requires ORGANIZATION scope.");
        }
        Long companyId = requiredTenant();
        int year = resolveFy(fy);
        Map<Long, EmployeeTaxDeclaration> byEmployee = repository.findAllByCompany_IdAndFinancialYearAndDeletedFalse(companyId, year)
                .stream().collect(Collectors.toMap(d -> d.getEmployee().getId(), Function.identity(), (a, b) -> a));
        return employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId).stream()
                .filter(e -> !EmploymentStatus.RESIGNED.equals(e.getStatus()) && !EmploymentStatus.TERMINATED.equals(e.getStatus())
                        || byEmployee.containsKey(e.getId()))
                .map(e -> TaxDeclarationDTO.of(e, year, byEmployee.get(e.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TaxDeclarationDTO getForEmployee(Long employeeId, Integer fy) {
        Employee employee = employeeInCompany(employeeId);
        requireAccess("SALARY_VIEW", employeeId, true);
        return get(employee, resolveFy(fy));
    }

    @Transactional
    public TaxDeclarationDTO saveForEmployee(Long employeeId, Integer fy, TaxDeclarationDTO request) {
        Employee employee = employeeInCompany(employeeId);
        requireAccess("SALARY_MANAGE", employeeId, false);
        return save(employee, resolveFy(fy), request, "HR");
    }

    @Transactional(readOnly = true)
    public TaxProjectionDTO projectForEmployee(Long employeeId, Integer fy) {
        Employee employee = employeeInCompany(employeeId);
        requireAccess("SALARY_VIEW", employeeId, true);
        return project(employee, resolveFy(fy));
    }

    // ----- shared -----

    private TaxDeclarationDTO get(Employee employee, int fy) {
        EmployeeTaxDeclaration d = repository
                .findByCompany_IdAndEmployee_IdAndFinancialYearAndDeletedFalse(requiredTenant(), employee.getId(), fy).orElse(null);
        return TaxDeclarationDTO.of(employee, fy, d);
    }

    private TaxDeclarationDTO save(Employee employee, int fy, TaxDeclarationDTO request, String actor) {
        Long companyId = requiredTenant();
        String regime = request.getRegime() == null ? "NEW" : request.getRegime().trim().toUpperCase(java.util.Locale.ROOT);
        if (!"NEW".equals(regime) && !"OLD".equals(regime)) {
            throw new BadRequestException("Regime must be NEW or OLD");
        }
        request.setRegime(regime);
        for (BigDecimal amount : List.of(nz(request.getSection80C()), nz(request.getSection80D()), nz(request.getRentPaid()),
                nz(request.getHomeLoanInterest()), nz(request.getNps80ccd1b()), nz(request.getOtherDeductions()))) {
            if (amount.signum() < 0) throw new BadRequestException("Declared amounts cannot be negative");
        }
        EmployeeTaxDeclaration d = repository
                .findByCompany_IdAndEmployee_IdAndFinancialYearAndDeletedFalse(companyId, employee.getId(), fy)
                .orElseGet(() -> {
                    EmployeeTaxDeclaration created = new EmployeeTaxDeclaration();
                    created.setCompany(employee.getCompany());
                    created.setEmployee(employee);
                    created.setFinancialYear(fy);
                    return created;
                });
        boolean isNew = d.getId() == null;
        request.applyTo(d);
        d.setSubmittedAt(LocalDateTime.now(IST));
        EmployeeTaxDeclaration saved = repository.save(d);
        auditLogService.log("EmployeeTaxDeclaration", saved.getId(), isNew ? "CREATE" : "UPDATE",
                actor + " " + (isNew ? "submitted" : "updated") + " the " + TaxDeclarationDTO.fyLabel(fy)
                        + " tax declaration for '" + employee.getFullName() + "' (" + regime + " regime)");
        return TaxDeclarationDTO.of(employee, fy, saved);
    }

    private TaxProjectionDTO project(Employee employee, int fy) {
        Long companyId = requiredTenant();
        EmployeeTaxDeclaration d = repository
                .findByCompany_IdAndEmployee_IdAndFinancialYearAndDeletedFalse(companyId, employee.getId(), fy).orElse(null);
        SalaryStructure structure = salaryStructureRepository
                .findByEmployee_Company_IdAndEmployeeIdAndActiveTrueAndDeletedFalse(companyId, employee.getId()).orElse(null);
        YearMonth asOf = YearMonth.now(IST);
        var newer = engine.project(companyId, employee, structure, fy, asOf, d, Regime.NEW);
        var older = engine.project(companyId, employee, structure, fy, asOf, d, Regime.OLD);
        Regime selected = StatutoryPayrollEngine.regimeOf(d);
        var chosen = selected == Regime.OLD ? older : newer;
        BigDecimal monthly = BigDecimal.ZERO;
        BigDecimal balance = chosen.result().totalTax().subtract(chosen.tdsDeductedSoFar());
        if (balance.signum() > 0 && chosen.monthsProjected() > 0) {
            monthly = balance.divide(BigDecimal.valueOf(chosen.monthsProjected()), 0, RoundingMode.HALF_UP);
        }
        String recommended = older.result().totalTax().compareTo(newer.result().totalTax()) < 0 ? "OLD" : "NEW";
        return new TaxProjectionDTO(fy, TaxDeclarationDTO.fyLabel(fy), selected.name(), structure != null,
                chosen.monthsFinalized(), chosen.monthsProjected(), chosen.tdsDeductedSoFar(), monthly,
                newer.result(), older.result(), recommended, engine.openingFor(companyId, employee.getId(), fy));
    }

    private int resolveFy(Integer fy) {
        int current = currentFinancialYear();
        if (fy == null) return current;
        if (fy < 2020 || fy > current + 1) throw new BadRequestException("Unsupported financial year: " + fy);
        return fy;
    }

    private Employee currentEmployee() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) throw new AccessDeniedException("Not signed in");
        Employee employee = employeeRepository.findByUser_UsernameAndDeletedFalse(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Your login is not linked to an employee profile"));
        if (employee.getCompany() == null || !employee.getCompany().getId().equals(requiredTenant())) {
            throw new AccessDeniedException("Employee profile belongs to another company");
        }
        return employee;
    }

    private Employee employeeInCompany(Long employeeId) {
        return employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
    }

    private void requireAccess(String code, Long employeeId, boolean allowSelf) {
        if (!authorizationService.isAllowed(code, "EMPLOYEE", employeeId)
                && !(allowSelf && employeeSecurity.isSelf(employeeId))) {
            throw new AccessDeniedException(code + " is not authorized for this employee.");
        }
    }

    private Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
