package com.haodaone.employee.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.ConflictException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.dto.CreateEmployeeRequest;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.org.repository.DesignationRepository;
import com.haodaone.org.repository.TeamRepository;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.repository.UserRepository;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.repository.SubscriptionService;
import com.haodaone.security.AuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmployeeServiceEmailUpdateTest {

    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
    private final DesignationRepository designationRepository = mock(DesignationRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final EmployeeService employeeService = new EmployeeService(employeeRepository, departmentRepository,
            designationRepository, teamRepository, auditLogService, userRepository, companyRepository,
            subscriptionService, authorizationService);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void updatesSameTenantEmailAfterNormalizingAndAuditsChange() {
        TenantContext.setCurrentTenant(10L);
        Employee employee = employee(10L, "old@example.com");
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(7L, 10L)).thenReturn(Optional.of(employee));
        when(employeeRepository.findByEmailIgnoreCaseAndDeletedFalse("new@example.com")).thenReturn(Optional.empty());
        when(employeeRepository.save(employee)).thenReturn(employee);

        EmployeeServiceEmailUpdateTestRequest request = new EmployeeServiceEmailUpdateTestRequest();
        CreateEmployeeRequest update = request.validRequest("  New@Example.com ");

        employeeService.update(7L, update);

        assertEquals("new@example.com", employee.getEmail());
        verify(auditLogService).log(eq("Employee"), eq(7L), eq("UPDATE"), contains("old@example.com -> new@example.com"));
    }

    @Test
    void rejectsDuplicateEmailBeforeMutatingEmployee() {
        TenantContext.setCurrentTenant(10L);
        Employee employee = employee(10L, "old@example.com");
        Employee duplicate = employee(10L, "new@example.com");
        duplicate.setId(8L);
        duplicate.setEmployeeCode("EMP002");
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(7L, 10L)).thenReturn(Optional.of(employee));
        when(employeeRepository.findByEmailIgnoreCaseAndDeletedFalse("new@example.com")).thenReturn(Optional.of(duplicate));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> employeeService.update(7L, new EmployeeServiceEmailUpdateTestRequest().validRequest("new@example.com")));

        assertEquals("An account already exists with this email address.", exception.getMessage());
        assertEquals("old@example.com", employee.getEmail());
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void cannotUpdateEmployeeOutsideCurrentTenant() {
        TenantContext.setCurrentTenant(10L);
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(7L, 10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> employeeService.update(7L, new EmployeeServiceEmailUpdateTestRequest().validRequest("new@example.com")));
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    private Employee employee(Long companyId, String email) {
        Company company = new Company();
        company.setId(companyId);
        Employee employee = new Employee();
        employee.setId(7L);
        employee.setEmployeeCode("EMP001");
        employee.setFirstName("Test");
        employee.setLastName("Employee");
        employee.setEmail(email);
        employee.setDateOfJoining(LocalDate.now());
        employee.setCompany(company);
        return employee;
    }

    private static class EmployeeServiceEmailUpdateTestRequest {
        CreateEmployeeRequest validRequest(String email) {
            CreateEmployeeRequest request = new CreateEmployeeRequest();
            request.setFirstName("Test");
            request.setLastName("Employee");
            request.setEmail(email);
            request.setDateOfJoining(LocalDate.now());
            return request;
        }
    }
}
