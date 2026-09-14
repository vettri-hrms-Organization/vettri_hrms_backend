package com.haodaone.tenant;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.dto.CreateEmployeeRequest;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.employee.service.EmployeeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class EmployeeTenantIsolationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @AfterEach
    public void cleanup() {
        TenantContext.clear();
        employeeRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    @Transactional
    public void createEmployee_assignedToCurrentTenant() {
        Company c = new Company();
        c.setName("HaodaPay");
        c = companyRepository.save(c);

        // set tenant in context
        TenantContext.setCurrentTenant(c.getId());

        CreateEmployeeRequest req = new CreateEmployeeRequest();
        req.setFirstName("Test");
        req.setLastName("User");
        req.setEmail("test.user@haodapay.local");
        req.setDateOfJoining(java.time.LocalDate.now());

        var dto = employeeService.create(req);
        Employee saved = employeeRepository.findById(dto.getId()).orElseThrow();
        Assertions.assertNotNull(saved.getCompany(), "Company must be set on created employee");
        Assertions.assertEquals(c.getId(), saved.getCompany().getId());
    }
}
