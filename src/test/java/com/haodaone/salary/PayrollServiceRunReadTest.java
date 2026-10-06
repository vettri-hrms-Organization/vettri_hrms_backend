package com.haodaone.salary;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.PayrollRun;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.PayrollRunRepository;
import com.haodaone.salary.service.PayrollService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class PayrollServiceRunReadTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private PayrollItemRepository payrollItemRepository;

    @Autowired
    private PayrollService payrollService;

    @MockitoBean
    private AuthorizationService authorizationService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void getRunMapsEmployeeAndPayrollItemFromLazyAssociations() {
        Company company = createCompany();
        Employee employee = createEmployee(company);
        PayrollRun run = createRun(company);
        createItem(company, run, employee);
        TenantContext.setCurrentTenant(company.getId());
        when(authorizationService.hasOrganizationScope("SALARY_VIEW")).thenReturn(true);

        var result = payrollService.getRun(run.getId());

        assertEquals("October 2026", result.getRun().getPeriodLabel());
        assertEquals(1, result.getRun().getTotalEmployees());
        assertEquals(1, result.getItems().size());
        var item = result.getItems().get(0);
        assertEquals(employee.getId(), item.getEmployeeId());
        assertEquals(employee.getFullName(), item.getEmployeeName());
        assertEquals(employee.getEmployeeCode(), item.getEmployeeCode());
        assertEquals(new BigDecimal("50000.00"), item.getComponents().getBasicSalary());
        assertEquals(new BigDecimal("50000.00"), item.getGrossSalary());
        assertEquals(new BigDecimal("5000.00"), item.getTotalDeductions());
        assertEquals(new BigDecimal("45000.00"), item.getNetSalary());
    }

    @Test
    void getRunDoesNotReturnAnotherCompanysRun() {
        Company ownerCompany = createCompany();
        PayrollRun run = createRun(ownerCompany);
        TenantContext.setCurrentTenant(createCompany().getId());
        when(authorizationService.hasOrganizationScope("SALARY_VIEW")).thenReturn(true);

        assertThrows(ResourceNotFoundException.class, () -> payrollService.getRun(run.getId()));
    }

    @Test
    void getRunStillRequiresOrganizationSalaryViewScope() {
        Company company = createCompany();
        PayrollRun run = createRun(company);
        TenantContext.setCurrentTenant(company.getId());
        when(authorizationService.hasOrganizationScope("SALARY_VIEW")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> payrollService.getRun(run.getId()));
    }

    private Company createCompany() {
        Company company = new Company();
        company.setName("Payroll run read " + UUID.randomUUID());
        return companyRepository.saveAndFlush(company);
    }

    private Employee createEmployee(Company company) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Employee employee = new Employee();
        employee.setEmployeeCode("P" + suffix);
        employee.setFirstName("Payroll");
        employee.setLastName("Employee");
        employee.setEmail(suffix + "@payroll-run.example");
        employee.setDateOfJoining(LocalDate.now().minusMonths(1));
        employee.setCompany(company);
        return employeeRepository.saveAndFlush(employee);
    }

    private PayrollRun createRun(Company company) {
        PayrollRun run = new PayrollRun();
        run.setCompany(company);
        run.setPeriodMonth(10);
        run.setPeriodYear(2026);
        run.setTotalEmployees(1);
        run.setTotalGross(new BigDecimal("1000000.00"));
        run.setTotalDeductions(new BigDecimal("100000.00"));
        run.setTotalNet(new BigDecimal("900000.00"));
        return payrollRunRepository.saveAndFlush(run);
    }

    private PayrollItem createItem(Company company, PayrollRun run, Employee employee) {
        SalaryComponents components = new SalaryComponents();
        components.setBasicSalary(new BigDecimal("50000.00"));
        components.setPf(new BigDecimal("5000.00"));

        PayrollItem item = new PayrollItem();
        item.setCompany(company);
        item.setPayrollRun(run);
        item.setEmployee(employee);
        item.setComponents(components);
        item.recalculate();
        return payrollItemRepository.saveAndFlush(item);
    }
}
