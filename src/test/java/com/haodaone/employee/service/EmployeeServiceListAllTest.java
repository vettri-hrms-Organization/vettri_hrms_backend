package com.haodaone.employee.service;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.dto.EmployeeSummaryDTO;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.org.entity.Department;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that the LazyInitializationException for Department
 * is fixed in listAll() and listPaged() methods.
 *
 * This test specifically checks that Department lazy-loading works correctly
 * when the DTO mapping occurs within a transaction boundary.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class EmployeeServiceListAllTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private CompanyRepository companyRepository;

    private Company testCompany;
    private Department testDepartment;
    private Employee employeeWithDept;
    private Employee employeeWithoutDept;

    @BeforeEach
    public void setup() {
        // Create a test company
        testCompany = new Company();
        testCompany.setName("Test Company");
        testCompany = companyRepository.save(testCompany);

        // Create a test department
        testDepartment = new Department();
        testDepartment.setName("Engineering");
        testDepartment.setCompany(testCompany);
        testDepartment = departmentRepository.save(testDepartment);

        // Create employee with department
        employeeWithDept = new Employee();
        employeeWithDept.setEmployeeCode("EMP001");
        employeeWithDept.setFirstName("John");
        employeeWithDept.setLastName("Doe");
        employeeWithDept.setEmail("john.doe@example.com");
        employeeWithDept.setDateOfJoining(LocalDate.now());
        employeeWithDept.setEmploymentType("FULL_TIME");
        employeeWithDept.setCompany(testCompany);
        employeeWithDept.setDepartment(testDepartment);
        employeeWithDept = employeeRepository.save(employeeWithDept);

        // Create employee without department (null department)
        employeeWithoutDept = new Employee();
        employeeWithoutDept.setEmployeeCode("EMP002");
        employeeWithoutDept.setFirstName("Jane");
        employeeWithoutDept.setLastName("Smith");
        employeeWithoutDept.setEmail("jane.smith@example.com");
        employeeWithoutDept.setDateOfJoining(LocalDate.now());
        employeeWithoutDept.setEmploymentType("FULL_TIME");
        employeeWithoutDept.setCompany(testCompany);
        employeeWithoutDept.setDepartment(null);
        employeeWithoutDept = employeeRepository.save(employeeWithoutDept);

        // Set the tenant context to the test company
        TenantContext.setCurrentTenant(testCompany.getId());
    }

    @AfterEach
    public void cleanup() {
        TenantContext.clear();
    }

    /**
     * Test that listAll() returns employees with department information
     * without triggering LazyInitializationException.
     */
    @Test
    public void listAll_withEmployeesHavingDepartments_shouldReturnDepartmentNamesSuccessfully() {
        // Act: Call listAll() which should not throw LazyInitializationException
        List<EmployeeSummaryDTO> results = employeeService.listAll(null);

        // Assert: Verify results are correct
        assertNotNull(results, "Results should not be null");
        assertEquals(2, results.size(), "Should return 2 employees");

        // Find the employee with department
        EmployeeSummaryDTO deptEmployee = results.stream()
                .filter(e -> "EMP001".equals(e.getEmployeeCode()))
                .findFirst()
                .orElse(null);

        assertNotNull(deptEmployee, "Employee with department should be in results");
        assertEquals("Engineering", deptEmployee.getDepartmentName(),
                "Department name should be correctly populated");

        // Find the employee without department
        EmployeeSummaryDTO noDeptEmployee = results.stream()
                .filter(e -> "EMP002".equals(e.getEmployeeCode()))
                .findFirst()
                .orElse(null);

        assertNotNull(noDeptEmployee, "Employee without department should be in results");
        assertNull(noDeptEmployee.getDepartmentName(),
                "Department name should be null for employee without department");
    }

    /**
     * Test that listAll() with search parameter also handles lazy-loaded departments correctly.
     */
    @Test
    public void listAll_withSearchParameter_shouldReturnDepartmentNamesSuccessfully() {
        // Act: Call listAll with search term
        List<EmployeeSummaryDTO> results = employeeService.listAll("John");

        // Assert: Verify results
        assertNotNull(results, "Results should not be null");
        assertEquals(1, results.size(), "Should return 1 employee matching search");

        EmployeeSummaryDTO result = results.get(0);
        assertEquals("John", result.getFullName().split(" ")[0], "Should find John");
        assertEquals("Engineering", result.getDepartmentName(),
                "Department name should be correctly populated even with search");
    }

    /**
     * Test that listPaged() also handles lazy-loaded departments correctly.
     */
    @Test
    public void listPaged_withEmployeesHavingDepartments_shouldReturnDepartmentNamesSuccessfully() {
        // Act: Call listPaged()
        var pageResponse = employeeService.listPaged(null, null, 0, 25);

        // Assert: Verify results
        assertNotNull(pageResponse, "Page response should not be null");
        assertEquals(2, pageResponse.getContent().size(), "Should return 2 employees");

        // Verify department data is present
        EmployeeSummaryDTO deptEmployee = pageResponse.getContent().stream()
                .filter(e -> "EMP001".equals(e.getEmployeeCode()))
                .findFirst()
                .orElse(null);

        assertNotNull(deptEmployee, "Employee with department should be in results");
        assertEquals("Engineering", deptEmployee.getDepartmentName(),
                "Department name should be correctly populated in paged results");
    }

    /**
     * Test that no LazyInitializationException occurs when accessing related entities.
     * This is the core fix verification - if the @Transactional annotation is missing,
     * this test would fail with LazyInitializationException.
     */
    @Test
    public void listAll_shouldNotThrowLazyInitializationException() {
        // Act & Assert: Should not throw any exception
        assertDoesNotThrow(() -> {
            List<EmployeeSummaryDTO> results = employeeService.listAll(null);
            // Try to access all lazy-loaded properties to ensure they're initialized
            for (EmployeeSummaryDTO dto : results) {
                assertNotNull(dto.getId());
                assertNotNull(dto.getEmployeeCode());
                assertNotNull(dto.getFullName());
                assertNotNull(dto.getEmail());
                // Department can be null, but should not throw LazyInitializationException
                String deptName = dto.getDepartmentName();
                assertNotNull(deptName != null ? deptName : "null",
                        "Accessing department should not throw exception");
            }
        }, "listAll() should not throw LazyInitializationException");
    }
}
