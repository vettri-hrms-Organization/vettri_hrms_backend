package com.haodaone.salary;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.salary.service.SalaryStructureService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.security.EmployeeSecurity;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class SalaryStructureServiceTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalaryStructureRepository salaryStructureRepository;

    @Autowired
    private SalaryStructureService salaryStructureService;

    @MockitoBean
    private AuthorizationService authorizationService;

    @MockitoBean
    private EmployeeSecurity employeeSecurity;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void getCurrentMapsEmployeeDetailsAndSalaryWhileEmployeeIsLazy() {
        Company company = createCompany();
        Employee employee = createEmployee(company);
        SalaryStructure structure = createSalaryStructure(company, employee);
        TenantContext.setCurrentTenant(company.getId());
        allowSalaryView(employee.getId());

        var result = salaryStructureService.getCurrent(employee.getId());

        assertEquals(employee.getId(), result.getEmployeeId());
        assertEquals(employee.getFullName(), result.getEmployeeName());
        assertEquals(employee.getEmployeeCode(), result.getEmployeeCode());
        assertEquals(structure.getEffectiveFrom(), result.getEffectiveFrom());
        assertEquals(new BigDecimal("1000.00"), result.getComponents().getBasicSalary());
        assertEquals(new BigDecimal("1500.00"), result.getGrossSalary());
        assertEquals(new BigDecimal("1400.00"), result.getNetSalary());
        assertEquals(true, result.isActive());
    }

    @Test
    void getCurrentReturnsNullWhenEmployeeHasNoActiveSalaryStructure() {
        Company company = createCompany();
        Employee employee = createEmployee(company);
        TenantContext.setCurrentTenant(company.getId());
        allowSalaryView(employee.getId());

        assertNull(salaryStructureService.getCurrent(employee.getId()));
    }

    @Test
    void getCurrentDoesNotReturnSalaryStructureAcrossTenantBoundary() {
        Company ownerCompany = createCompany();
        Company currentCompany = createCompany();
        Employee employee = createEmployee(ownerCompany);
        createSalaryStructure(ownerCompany, employee);
        TenantContext.setCurrentTenant(currentCompany.getId());
        allowSalaryView(employee.getId());

        assertNull(salaryStructureService.getCurrent(employee.getId()));
    }

    @Test
    void getCurrentStillDeniesRequestsWithoutSalaryViewOrSelfAccess() {
        Company company = createCompany();
        Employee employee = createEmployee(company);
        TenantContext.setCurrentTenant(company.getId());
        when(authorizationService.isAllowed("SALARY_VIEW", "EMPLOYEE", employee.getId())).thenReturn(false);
        when(employeeSecurity.isSelf(employee.getId())).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> salaryStructureService.getCurrent(employee.getId()));
    }

    private void allowSalaryView(Long employeeId) {
        when(authorizationService.isAllowed(eq("SALARY_VIEW"), eq("EMPLOYEE"), eq(employeeId))).thenReturn(true);
        when(employeeSecurity.isSelf(employeeId)).thenReturn(false);
    }

    private Company createCompany() {
        Company company = new Company();
        company.setName("Salary structure " + UUID.randomUUID());
        return companyRepository.saveAndFlush(company);
    }

    private Employee createEmployee(Company company) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Employee employee = new Employee();
        employee.setEmployeeCode("S" + suffix);
        employee.setFirstName("Salary");
        employee.setLastName("Employee");
        employee.setEmail(suffix + "@salary-structure.example");
        employee.setDateOfJoining(LocalDate.now().minusMonths(1));
        employee.setCompany(company);
        return employeeRepository.saveAndFlush(employee);
    }

    private SalaryStructure createSalaryStructure(Company company, Employee employee) {
        SalaryComponents components = new SalaryComponents();
        components.setBasicSalary(new BigDecimal("1000.00"));
        components.setHra(new BigDecimal("500.00"));
        components.setPf(new BigDecimal("100.00"));

        SalaryStructure structure = new SalaryStructure();
        structure.setCompany(company);
        structure.setEmployee(employee);
        structure.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        structure.setComponents(components);
        structure.recalculate();
        return salaryStructureRepository.saveAndFlush(structure);
    }
}
