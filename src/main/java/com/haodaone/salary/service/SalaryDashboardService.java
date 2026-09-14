package com.haodaone.salary.service;

import com.haodaone.audit.entity.AuditLog;
import com.haodaone.audit.repository.AuditLogRepository;
import com.haodaone.salary.dto.SalaryDashboardDTO;
import com.haodaone.salary.entity.PayrollItemStatus;
import com.haodaone.salary.entity.PayrollRun;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.PayrollRunRepository;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.tenant.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Read-only aggregation for the Salary Dashboard - every figure is computed live from SalaryStructure/PayrollRun/PayrollItem, nothing is cached. */
@Service
public class SalaryDashboardService {

    private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    /** Entity names this module writes to the shared audit log - see AuditLogService.log() call sites in this package. */
    private static final Set<String> SALARY_AUDIT_ENTITIES = Set.of("SalaryStructure", "PayrollRun", "PayrollItem");

    private final SalaryStructureRepository salaryStructureRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final AuditLogRepository auditLogRepository;

    public SalaryDashboardService(SalaryStructureRepository salaryStructureRepository, PayrollRunRepository payrollRunRepository,
                                   PayrollItemRepository payrollItemRepository, AuditLogRepository auditLogRepository) {
        this.salaryStructureRepository = salaryStructureRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public SalaryDashboardDTO getSummary() {
        requiredTenant();
        SalaryDashboardDTO dto = new SalaryDashboardDTO();
        dto.setOverview(buildOverview());
        dto.setDepartmentDistribution(buildDepartmentDistribution());
        dto.setPayrollTrend(buildPayrollTrend());
        dto.setRecentActivity(buildRecentActivity());
        return dto;
    }

    private SalaryDashboardDTO.Overview buildOverview() {
        Long companyId = requiredTenant();
        SalaryDashboardDTO.Overview overview = new SalaryDashboardDTO.Overview();
        overview.setActiveEmployeesWithSalary(salaryStructureRepository.countActive(companyId));
        overview.setMonthlyPayrollCost(salaryStructureRepository.sumActiveNetSalary(companyId));
        overview.setAverageSalary(round(salaryStructureRepository.averageActiveNetSalary(companyId)));
        overview.setHighestSalary(salaryStructureRepository.maxActiveNetSalary(companyId));
        overview.setLowestSalary(salaryStructureRepository.minActiveNetSalary(companyId));
        overview.setUpcomingPayrollDate(payrollRunRepository.findNextUpcomingPayDate(companyId, LocalDate.now()));

        payrollRunRepository.findTopByCompany_IdAndDeletedFalseOrderByPeriodYearDescPeriodMonthDesc(companyId).ifPresent(run -> {
            long processed = payrollItemRepository.countByPayrollRunIdAndStatusAndDeletedFalse(run.getId(), PayrollItemStatus.PROCESSED)
                    + payrollItemRepository.countByPayrollRunIdAndStatusAndDeletedFalse(run.getId(), PayrollItemStatus.PAID);
            long total = payrollItemRepository.countByPayrollRunIdAndDeletedFalse(run.getId());
            overview.setEmployeesProcessed((int) processed);
            overview.setEmployeesPending((int) (total - processed));
            overview.setPayrollCompletionPercent(total == 0 ? 0 : (int) Math.round((processed * 100.0) / total));
            overview.setCurrentPeriodLabel(monthLabel(run));
            overview.setCurrentPeriodStatus(run.getStatus());
        });

        if (overview.getCurrentPeriodLabel() == null) {
            LocalDate now = LocalDate.now();
            overview.setCurrentPeriodLabel(MONTH_NAMES[now.getMonthValue() - 1] + " " + now.getYear());
            overview.setCurrentPeriodStatus("NOT_STARTED");
            overview.setEmployeesPending((int) overview.getActiveEmployeesWithSalary());
        }
        return overview;
    }

    private List<SalaryDashboardDTO.DepartmentAmount> buildDepartmentDistribution() {
        return salaryStructureRepository.sumActiveNetSalaryByDepartment(requiredTenant()).stream()
                .map(row -> new SalaryDashboardDTO.DepartmentAmount((String) row[0], (Long) row[1], (BigDecimal) row[2]))
                .toList();
    }

    private List<SalaryDashboardDTO.MonthlyTrendPoint> buildPayrollTrend() {
        List<PayrollRun> recent = payrollRunRepository.findRecentNonDraftRuns(requiredTenant());
        return recent.stream()
                .limit(12)
                .sorted(Comparator.comparing(PayrollRun::getPeriodYear).thenComparing(PayrollRun::getPeriodMonth))
                .map(r -> new SalaryDashboardDTO.MonthlyTrendPoint(monthLabel(r), r.getPeriodMonth(), r.getPeriodYear(),
                        r.getTotalGross(), r.getTotalDeductions(), r.getTotalNet()))
                .toList();
    }

    private List<SalaryDashboardDTO.RecentActivity> buildRecentActivity() {
        List<AuditLog> logs = auditLogRepository.findByCompanyIdAndEntityNameInOrderByPerformedAtDesc(requiredTenant(), SALARY_AUDIT_ENTITIES, PageRequest.of(0, 10)).getContent();
        return logs.stream()
                .map(l -> new SalaryDashboardDTO.RecentActivity(l.getEntityName(), l.getAction(), l.getDetails(), l.getPerformedBy(), l.getPerformedAt()))
                .toList();
    }

    private static BigDecimal round(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String monthLabel(PayrollRun run) {
        return MONTH_NAMES[run.getPeriodMonth() - 1] + " " + run.getPeriodYear();
    }

    private Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }
}
