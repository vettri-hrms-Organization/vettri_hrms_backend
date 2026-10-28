package com.haodaone.tenant;

import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import com.haodaone.security.CompanySecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

public class CompanySecurityTest {

    private EmployeeRepository employeeRepository = Mockito.mock(EmployeeRepository.class);
    private MonitoredDeviceRepository monitoredDeviceRepository = Mockito.mock(MonitoredDeviceRepository.class);
    private UserRepository userRepository = Mockito.mock(UserRepository.class);

    private CompanySecurity companySecurity;

    @BeforeEach
    void setup() {
        companySecurity = new CompanySecurity(employeeRepository, monitoredDeviceRepository, userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdmin_canManageAnyEmployee() {
        // authentication with SUPER_ADMIN authority
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("sysadmin", null, "ROLE_SUPER_ADMIN"));

        assertTrue(companySecurity.isSuperAdmin());
        assertTrue(companySecurity.canManageEmployee(123L)); // no repository interaction required for super admin
    }

    @Test
    void companyAdmin_sameCompany_canManageEmployee() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null, "ROLE_COMPANY_ADMIN"));

        User current = new User();
        Company c = new Company();
        c.setId(1L);
        current.setCompany(c);

        when(userRepository.findByUsernameAndDeletedFalse("alice")).thenReturn(Optional.of(current));

        Employee target = new Employee();
        target.setCompany(c);
        when(employeeRepository.findById(42L)).thenReturn(Optional.of(target));

        assertTrue(companySecurity.canManageEmployee(42L));
    }

    @Test
    void companyAdmin_differentCompany_cannotManageEmployee() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob", null, "ROLE_COMPANY_ADMIN"));

        User current = new User();
        Company c1 = new Company();
        c1.setId(1L);
        current.setCompany(c1);

        Company c2 = new Company();
        c2.setId(2L);

        when(userRepository.findByUsernameAndDeletedFalse("bob")).thenReturn(Optional.of(current));

        Employee target = new Employee();
        target.setCompany(c2);
        when(employeeRepository.findById(anyLong())).thenReturn(Optional.of(target));

        assertFalse(companySecurity.canManageEmployee(99L));
    }

    @Test
    void hrAdmin_withoutImportPermission_cannotImportEmployees() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("hradmin", null, "ROLE_HR_ADMIN"));

        User current = new User();
        Company company = new Company();
        company.setId(7L);
        current.setCompany(company);

        when(userRepository.findByUsernameAndDeletedFalse("hradmin")).thenReturn(Optional.of(current));

        assertFalse(companySecurity.canImportEmployees());
    }

    @Test
    void companyAdmin_withImportPermission_canImportEmployees() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("company-admin", null, "ROLE_COMPANY_ADMIN", "EMPLOYEE_IMPORT"));

        User current = new User();
        Company company = new Company();
        company.setId(7L);
        current.setCompany(company);

        when(userRepository.findByUsernameAndDeletedFalse("company-admin")).thenReturn(Optional.of(current));

        assertTrue(companySecurity.canImportEmployees());
    }

    // ---- Permission-based checks (with AuthorizationService wired, as in production) ----

    private CompanySecurity permissionAwareSecurity(com.haodaone.security.AuthorizationService authorizationService) {
        return new CompanySecurity(employeeRepository, monitoredDeviceRepository, userRepository, authorizationService);
    }

    private Company companyWithId(long id) {
        Company company = new Company();
        company.setId(id);
        return company;
    }

    @Test
    void hrExecutive_withOrganizationScopedCreate_canCreateEmployee_withoutHrAdminRoleName() {
        var authorizationService = Mockito.mock(com.haodaone.security.AuthorizationService.class);
        when(authorizationService.hasOrganizationScope("EMPLOYEE_CREATE")).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("hr-exec", null, "ROLE_HR_EXECUTIVE", "EMPLOYEE_CREATE"));
        User current = new User();
        current.setCompany(companyWithId(1L));
        when(userRepository.findByUsernameAndDeletedFalse("hr-exec")).thenReturn(Optional.of(current));

        assertTrue(permissionAwareSecurity(authorizationService).canCreateEmployee());
    }

    @Test
    void hrAdminRoleName_withoutCreateScope_cannotCreateEmployee() {
        var authorizationService = Mockito.mock(com.haodaone.security.AuthorizationService.class);
        when(authorizationService.hasOrganizationScope("EMPLOYEE_CREATE")).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("hr-admin", null, "ROLE_HR_ADMIN"));
        User current = new User();
        current.setCompany(companyWithId(1L));
        when(userRepository.findByUsernameAndDeletedFalse("hr-admin")).thenReturn(Optional.of(current));

        assertFalse(permissionAwareSecurity(authorizationService).canCreateEmployee());
    }

    @Test
    void employeeManageHolder_canManageSameCompanyEmployee_butNotAnotherCompanys() {
        var authorizationService = Mockito.mock(com.haodaone.security.AuthorizationService.class);
        when(authorizationService.isAllowed(Mockito.eq("EMPLOYEE_MANAGE"), Mockito.eq("EMPLOYEE"), anyLong())).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("hr-exec", null, "ROLE_HR_EXECUTIVE", "EMPLOYEE_MANAGE"));
        User current = new User();
        current.setCompany(companyWithId(1L));
        when(userRepository.findByUsernameAndDeletedFalse("hr-exec")).thenReturn(Optional.of(current));

        Employee sameCompany = new Employee();
        sameCompany.setCompany(companyWithId(1L));
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(sameCompany));
        Employee otherCompany = new Employee();
        otherCompany.setCompany(companyWithId(2L));
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(otherCompany));

        CompanySecurity security = permissionAwareSecurity(authorizationService);
        assertTrue(security.canManageEmployee(10L));
        assertFalse(security.canManageEmployee(20L));
    }

    @Test
    void sameCompanyEmployee_isNotManageable_withoutEmployeeManageScope() {
        var authorizationService = Mockito.mock(com.haodaone.security.AuthorizationService.class);
        when(authorizationService.isAllowed(Mockito.eq("EMPLOYEE_MANAGE"), Mockito.eq("EMPLOYEE"), anyLong())).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("viewer", null, "ROLE_HR_COORDINATOR"));
        User current = new User();
        current.setCompany(companyWithId(1L));
        when(userRepository.findByUsernameAndDeletedFalse("viewer")).thenReturn(Optional.of(current));
        Employee sameCompany = new Employee();
        sameCompany.setCompany(companyWithId(1L));
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(sameCompany));

        assertFalse(permissionAwareSecurity(authorizationService).canManageEmployee(10L));
    }
}
