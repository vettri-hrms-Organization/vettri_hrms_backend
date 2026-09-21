package com.haodaone.security;

import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.attendance.repository.WfhRequestRepository;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.RolePermissionScope;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final LeaveRequestRepository leaveRequestRepository = mock(LeaveRequestRepository.class);
    private final WfhRequestRepository wfhRequestRepository = mock(WfhRequestRepository.class);
    private final AuthorizationService service = new AuthorizationService(userRepository, employeeRepository, leaveRequestRepository, wfhRequestRepository);

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void selfScopeAllowsOwnEmployeeOnly() {
        Company company = company(1L);
        User user = user(10L, company);
        Employee current = employee(10L, company);
        Employee other = employee(11L, company);
        grant(user, "EMPLOYEE_VIEW", PermissionScope.SELF);
        authenticate(user, "EMPLOYEE_VIEW");
        TenantContext.setCurrentTenant(1L);
        when(employeeRepository.findByUser_IdAndDeletedFalse(10L)).thenReturn(Optional.of(current));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(current));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(11L, 1L)).thenReturn(Optional.of(other));

        assertTrue(service.isAllowed("EMPLOYEE_VIEW", "EMPLOYEE", 10L));
        assertFalse(service.isAllowed("EMPLOYEE_VIEW", "EMPLOYEE", 11L));
    }

    @Test
    void teamScopeAllowsDirectReportsOnly() {
        Company company = company(1L);
        User user = user(10L, company);
        Employee manager = employee(10L, company);
        Employee report = employee(11L, company);
        report.setReportingManager(manager);
        Employee unrelated = employee(12L, company);
        grant(user, "ATTENDANCE_VIEW", PermissionScope.TEAM);
        authenticate(user, "ATTENDANCE_VIEW");
        TenantContext.setCurrentTenant(1L);
        when(employeeRepository.findByUser_IdAndDeletedFalse(10L)).thenReturn(Optional.of(manager));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(11L, 1L)).thenReturn(Optional.of(report));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(12L, 1L)).thenReturn(Optional.of(unrelated));

        assertTrue(service.isAllowed("ATTENDANCE_VIEW", "EMPLOYEE", 11L));
        assertFalse(service.isAllowed("ATTENDANCE_VIEW", "EMPLOYEE", 12L));
    }

    @Test
    void tenantLookupPreventsCrossCompanyResourceAccess() {
        Company company = company(1L);
        User user = user(10L, company);
        grant(user, "EMPLOYEE_VIEW", PermissionScope.ORGANIZATION);
        authenticate(user, "EMPLOYEE_VIEW");
        TenantContext.setCurrentTenant(1L);
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(eq(99L), eq(1L))).thenReturn(Optional.empty());

        assertFalse(service.isAllowed("EMPLOYEE_VIEW", "EMPLOYEE", 99L));
    }

    @Test
    void organizationScopeDoesNotRequireCurrentUserEmployeeRecord() {
        Company company = company(1L);
        User user = user(10L, company);
        Employee target = employee(11L, company);
        grant(user, "EMPLOYEE_VIEW", PermissionScope.ORGANIZATION);
        authenticate(user, "EMPLOYEE_VIEW");
        TenantContext.setCurrentTenant(1L);
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(11L, 1L)).thenReturn(Optional.of(target));

        assertTrue(service.isAllowed("EMPLOYEE_VIEW", "EMPLOYEE", 11L));
    }

    private void authenticate(User user, String permission) {
        when(userRepository.findByUsernameAndDeletedFalse(user.getUsername())).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserPrincipal(user), null, Set.of(() -> permission)));
    }

    private void grant(User user, String code, PermissionScope scope) {
        Permission permission = new Permission();
        permission.setCode(code);
        Role role = new Role();
        role.setName("TEST");
        role.setPermissions(Set.of(permission));
        RolePermissionScope roleScope = new RolePermissionScope();
        roleScope.setRole(role);
        roleScope.setPermission(permission);
        roleScope.setScope(scope);
        role.setPermissionScopes(Set.of(roleScope));
        user.setRoles(Set.of(role));
    }

    private User user(Long id, Company company) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setActive(true);
        user.setAccountStatus("ACTIVE");
        user.setCompany(company);
        return user;
    }

    private Employee employee(Long id, Company company) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setCompany(company);
        return employee;
    }

    private Company company(Long id) {
        Company company = new Company();
        company.setId(id);
        return company;
    }
}
