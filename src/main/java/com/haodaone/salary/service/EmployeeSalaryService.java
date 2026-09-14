package com.haodaone.salary.service;

import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.salary.dto.EmployeeSalaryDetailDTO;
import com.haodaone.salary.dto.EmployeeSalarySummaryDTO;
import com.haodaone.salary.dto.PayrollItemDTO;
import com.haodaone.salary.dto.SalaryStructureDTO;
import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Read-side for the Employee Salary List and Salary Details pages -
 * combines Employee (identity/org placement), SalaryStructure (current pay)
 * and PayrollItem (payroll history) without touching any of those modules'
 * own write paths.
 *
 * Filtering (search/department/status) runs in the database via
 * EmployeeRepository.searchForPayroll(); sorting and pagination run in
 * memory afterwards because several sortable columns (gross/net salary,
 * payroll status) live on a joined table rather than on Employee itself.
 * This mirrors the scale assumption the rest of the app already makes -
 * EmployeeController.listAll() returns its full result set unpaginated -
 * so it's a size the JVM handles trivially, just wrapped in a real
 * Spring Data Page so the UI gets proper pagination.
 */
@Service
public class EmployeeSalaryService {

    private final EmployeeRepository employeeRepository;
    private final SalaryStructureRepository salaryStructureRepository;
    private final PayrollItemRepository payrollItemRepository;

    public EmployeeSalaryService(EmployeeRepository employeeRepository, SalaryStructureRepository salaryStructureRepository,
                                  PayrollItemRepository payrollItemRepository) {
        this.employeeRepository = employeeRepository;
        this.salaryStructureRepository = salaryStructureRepository;
        this.payrollItemRepository = payrollItemRepository;
    }

    public Page<EmployeeSalarySummaryDTO> list(String search, Long departmentId, String status, String sortBy, String sortDir, Pageable pageable) {
        Long companyId = requiredTenant();
        List<Employee> employees = employeeRepository.searchForPayroll(companyId,
                (search == null || search.isBlank()) ? null : search.trim(), departmentId, status);

        Map<Long, SalaryStructure> structuresByEmployee = salaryStructureRepository.findAllActiveByEmployeeId(companyId);
        Map<Long, PayrollItem> latestItemByEmployee = payrollItemRepository.findLatestByEmployeeId(companyId);

        List<EmployeeSalarySummaryDTO> rows = employees.stream()
                .map(e -> EmployeeSalarySummaryDTO.build(e, structuresByEmployee.get(e.getId()), latestItemByEmployee.get(e.getId())))
                .sorted(comparator(sortBy, sortDir))
                .toList();

        int start = Math.min((int) pageable.getOffset(), rows.size());
        int end = Math.min(start + pageable.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(start, end), pageable, rows.size());
    }

    public EmployeeSalaryDetailDTO getDetail(Long employeeId) {
        Long companyId = requiredTenant();
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        SalaryStructureDTO current = salaryStructureRepository.findByEmployee_Company_IdAndEmployeeIdAndActiveTrueAndDeletedFalse(companyId, employeeId)
                .map(SalaryStructureDTO::from).orElse(null);

        List<SalaryStructureDTO> history = salaryStructureRepository
            .findByEmployee_Company_IdAndEmployeeIdAndDeletedFalseOrderByEffectiveFromDescCreatedAtDesc(companyId, employeeId)
                .stream().map(SalaryStructureDTO::from).toList();

        List<PayrollItemDTO> payrollHistory = payrollItemRepository.findByEmployee_Company_IdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(companyId, employeeId)
                .stream().map(PayrollItemDTO::from).toList();

        return EmployeeSalaryDetailDTO.build(employee, current, history, payrollHistory);
    }

    private Comparator<EmployeeSalarySummaryDTO> comparator(String sortBy, String sortDir) {
        Comparator<EmployeeSalarySummaryDTO> comparator = switch (sortBy == null ? "" : sortBy) {
            case "grossSalary" -> Comparator.comparing(EmployeeSalarySummaryDTO::getGrossSalary, EmployeeSalaryService::nullsFirstBigDecimal);
            case "netSalary" -> Comparator.comparing(EmployeeSalarySummaryDTO::getNetSalary, EmployeeSalaryService::nullsFirstBigDecimal);
            case "basicSalary" -> Comparator.comparing(EmployeeSalarySummaryDTO::getBasicSalary, EmployeeSalaryService::nullsFirstBigDecimal);
            case "department" -> Comparator.comparing(EmployeeSalarySummaryDTO::getDepartmentName, Comparator.nullsFirst(String::compareToIgnoreCase));
            case "payrollStatus" -> Comparator.comparing(EmployeeSalarySummaryDTO::getPayrollStatus, Comparator.nullsFirst(String::compareToIgnoreCase));
            case "lastPayrollDate" -> Comparator.comparing(EmployeeSalarySummaryDTO::getLastPayrollDate, Comparator.nullsFirst(Comparator.naturalOrder()));
            default -> Comparator.comparing(EmployeeSalarySummaryDTO::getEmployeeName, Comparator.nullsFirst(String::compareToIgnoreCase));
        };
        return "desc".equalsIgnoreCase(sortDir) ? comparator.reversed() : comparator;
    }

    private static int nullsFirstBigDecimal(BigDecimal a, BigDecimal b) {
        return Comparator.nullsFirst(Comparator.<BigDecimal>naturalOrder()).compare(a, b);
    }

    private Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }
}
