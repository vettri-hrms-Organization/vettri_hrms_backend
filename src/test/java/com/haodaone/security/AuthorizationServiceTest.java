package com.haodaone.security;

import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.org.entity.Team;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.attendance.repository.WfhRequestRepository;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.RolePermissionScope;
import com.haodaone.user.entity.User;
import com.haodaone.user.entity.UserPermissionGrant;
import com.haodaone.user.repository.UserRepository;
import com.haodaone.user.repository.UserPermissionGrantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final LeaveRequestRepository leaveRequestRepository = mock(LeaveRequestRepository.class);
    private final WfhRequestRepository wfhRequestRepository = mock(WfhRequestRepository.class);
    private final UserPermissionGrantRepository permissionGrantRepository = mock(UserPermissionGrantRepository.class);
    private final AuthorizationService service = new AuthorizationService(
            userRepository, employeeRepository, leaveRequestRepository, wfhRequestRepository, permissionGrantRepository);

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
    void teamScopeAllowsTeamMembersButNotDirectReportsFromOtherTeams() {
        Company company = company(1L);
        User user = user(10L, company);
        Employee manager = employee(10L, company);
        Team development = team(20L);
        Team marketing = team(21L);
        manager.setTeam(development);
        Employee teammate = employee(11L, company);
        teammate.setTeam(development);
        Employee directReportOutsideTeam = employee(12L, company);
        directReportOutsideTeam.setTeam(marketing);
        directReportOutsideTeam.setReportingManager(manager);
        grant(user, "ATTENDANCE_VIEW", PermissionScope.TEAM);
        authenticate(user, "ATTENDANCE_VIEW");
        TenantContext.setCurrentTenant(1L);
        when(employeeRepository.findByUser_IdAndDeletedFalse(10L)).thenReturn(Optional.of(manager));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(11L, 1L)).thenReturn(Optional.of(teammate));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(12L, 1L)).thenReturn(Optional.of(directReportOutsideTeam));

        assertTrue(service.isAllowed("ATTENDANCE_VIEW", "EMPLOYEE", 11L));
        assertFalse(service.isAllowed("ATTENDANCE_VIEW", "EMPLOYEE", 12L));
    }

    @Test
    void missingScopeDoesNotFallBackToOrganization() {
        Company company = company(1L);
        User user = user(10L, company);
        Employee target = employee(11L, company);
        Permission permission = new Permission();
        permission.setCode("EMPLOYEE_VIEW");
        Role role = new Role();
        role.setName("TEST");
        role.setPermissions(Set.of(permission));
        user.setRoles(Set.of(role));
        authenticate(user, "EMPLOYEE_VIEW");
        TenantContext.setCurrentTenant(1L);
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(11L, 1L)).thenReturn(Optional.of(target));

        assertFalse(service.isAllowed("EMPLOYEE_VIEW", "EMPLOYEE", 11L));
    }

    @Test
    void teamEmployeeListUsesTeamMembership() {
        Company company = company(1L);
        User user = user(10L, company);
        Employee manager = employee(10L, company);
        manager.setTeam(team(20L));
        grant(user, "MONITORING_VIEW", PermissionScope.TEAM);
        authenticate(user, "MONITORING_VIEW");
        TenantContext.setCurrentTenant(1L);
        when(employeeRepository.findByUser_IdAndDeletedFalse(10L)).thenReturn(Optional.of(manager));
        when(employeeRepository.findIdsByCompanyAndTeam(1L, 20L)).thenReturn(java.util.List.of(10L, 11L));

        assertEquals(Optional.of(Set.of(10L, 11L)), service.resolveEmployeeIds("MONITORING_VIEW"));
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

    @Test
    void userGrantAloneAuthorizesWithinItsStoredScope() {
        Company company = company(1L);
        User user = user(10L, company);
        Employee current = employee(10L, company);
        Employee teammate = employee(11L, company);
        current.setTeam(team(20L));
        teammate.setTeam(team(20L));
        UserPermissionGrant grant = userGrant(company, user, "MONITORING_VIEW", PermissionScope.TEAM);
        when(employeeRepository.findByUser_IdAndDeletedFalse(10L)).thenReturn(Optional.of(current));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(11L, 1L)).thenReturn(Optional.of(teammate));
        authenticateWithGrants(user, "MONITORING_VIEW", grant);
        TenantContext.setCurrentTenant(1L);

        assertTrue(service.isAllowed("MONITORING_VIEW", "EMPLOYEE", 11L));
        assertEquals(Set.of(PermissionScope.TEAM), service.getScopes("MONITORING_VIEW"));
    }

    @Test
    void selfUserGrantAllowsOnlyTheRecipientsOwnEmployeeRecord() {
        Company company = company(1L);
        User user = user(10L, company);
        Employee current = employee(10L, company);
        Employee other = employee(11L, company);
        UserPermissionGrant grant = userGrant(company, user, "EMPLOYEE_VIEW", PermissionScope.SELF);
        when(employeeRepository.findByUser_IdAndDeletedFalse(10L)).thenReturn(Optional.of(current));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(current));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(11L, 1L)).thenReturn(Optional.of(other));
        authenticateWithGrants(user, "EMPLOYEE_VIEW", grant);
        TenantContext.setCurrentTenant(1L);

        assertTrue(service.isAllowed("EMPLOYEE_VIEW", "EMPLOYEE", 10L));
        assertFalse(service.isAllowed("EMPLOYEE_VIEW", "EMPLOYEE", 11L));
    }

    @Test
    void departmentUserGrantUsesDepartmentMembership() {
        Company company = company(1L);
        User user = user(10L, company);
        Employee current = employee(10L, company);
        Employee sameDepartment = employee(11L, company);
        Employee otherDepartment = employee(12L, company);
        com.haodaone.org.entity.Department department = new com.haodaone.org.entity.Department();
        department.setId(31L);
        current.setDepartment(department);
        sameDepartment.setDepartment(department);
        com.haodaone.org.entity.Department anotherDepartment = new com.haodaone.org.entity.Department();
        anotherDepartment.setId(32L);
        otherDepartment.setDepartment(anotherDepartment);
        UserPermissionGrant grant = userGrant(company, user, "EMPLOYEE_VIEW", PermissionScope.DEPARTMENT);
        when(employeeRepository.findByUser_IdAndDeletedFalse(10L)).thenReturn(Optional.of(current));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(11L, 1L)).thenReturn(Optional.of(sameDepartment));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(12L, 1L)).thenReturn(Optional.of(otherDepartment));
        authenticateWithGrants(user, "EMPLOYEE_VIEW", grant);
        TenantContext.setCurrentTenant(1L);

        assertTrue(service.isAllowed("EMPLOYEE_VIEW", "EMPLOYEE", 11L));
        assertFalse(service.isAllowed("EMPLOYEE_VIEW", "EMPLOYEE", 12L));
    }

    @Test
    void organizationUserGrantIsLimitedToTheCurrentTenant() {
        Company company = company(1L);
        User user = user(10L, company);
        Employee target = employee(11L, company);
        UserPermissionGrant grant = userGrant(company, user, "REPORTS_VIEW", PermissionScope.ORGANIZATION);
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(11L, 1L)).thenReturn(Optional.of(target));
        authenticateWithGrants(user, "REPORTS_VIEW", grant);
        TenantContext.setCurrentTenant(1L);

        assertTrue(service.isAllowed("REPORTS_VIEW", "EMPLOYEE", 11L));
        assertTrue(service.isAllowed("REPORTS_VIEW", null, null));
    }

    @Test
    void customUserGrantIsNotEffectiveUntilTargetResolutionExists() {
        Company company = company(1L);
        User user = user(10L, company);
        UserPermissionGrant grant = userGrant(company, user, "EMPLOYEE_VIEW", PermissionScope.CUSTOM);
        authenticateWithGrants(user, "EMPLOYEE_VIEW", grant);
        TenantContext.setCurrentTenant(1L);

        assertEquals(Set.of(), service.getScopes("EMPLOYEE_VIEW"));
        assertFalse(service.isAllowed("EMPLOYEE_VIEW", null, null));
    }

    @Test
    void roleAndUserGrantScopesAreAdditiveWithoutWideningEachOther() {
        Company company = company(1L);
        User user = user(10L, company);
        grant(user, "MONITORING_VIEW", PermissionScope.TEAM);
        UserPermissionGrant grant = userGrant(company, user, "RECRUITMENT_MANAGE", PermissionScope.DEPARTMENT);
        authenticateWithGrants(user, "MONITORING_VIEW", grant);
        TenantContext.setCurrentTenant(1L);

        assertEquals(Set.of(PermissionScope.TEAM), service.getScopes("MONITORING_VIEW"));
        assertEquals(Set.of(PermissionScope.DEPARTMENT), service.getScopes("RECRUITMENT_MANAGE"));
    }

    @Test
    void revokedUserGrantDoesNotAuthorize() {
        Company company = company(1L);
        User user = user(10L, company);
        UserPermissionGrant grant = userGrant(company, user, "MONITORING_VIEW", PermissionScope.ORGANIZATION);
        grant.setRevokedAt(java.time.LocalDateTime.now());
        authenticateWithGrants(user, "MONITORING_VIEW", grant);
        TenantContext.setCurrentTenant(1L);

        assertFalse(service.isAllowed("MONITORING_VIEW", null, null));
        assertEquals(Set.of(), service.getScopes("MONITORING_VIEW"));
    }

    @Test
    void departmentScopeRequiresSameDepartmentAndOrganizationIsCompanyBound() {
        Company company = company(1L);
        User user = user(10L, company);
        Employee current = employee(10L, company);
        Employee sameDepartment = employee(11L, company);
        Employee otherDepartment = employee(12L, company);
        com.haodaone.org.entity.Department department = new com.haodaone.org.entity.Department();
        department.setId(31L);
        current.setDepartment(department);
        sameDepartment.setDepartment(department);
        com.haodaone.org.entity.Department anotherDepartment = new com.haodaone.org.entity.Department();
        anotherDepartment.setId(32L);
        otherDepartment.setDepartment(anotherDepartment);
        grant(user, "EMPLOYEE_VIEW", PermissionScope.DEPARTMENT);
        authenticate(user, "EMPLOYEE_VIEW");
        TenantContext.setCurrentTenant(1L);
        when(employeeRepository.findByUser_IdAndDeletedFalse(10L)).thenReturn(Optional.of(current));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(11L, 1L)).thenReturn(Optional.of(sameDepartment));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(12L, 1L)).thenReturn(Optional.of(otherDepartment));

        assertTrue(service.isAllowed("EMPLOYEE_VIEW", "EMPLOYEE", 11L));
        assertFalse(service.isAllowed("EMPLOYEE_VIEW", "EMPLOYEE", 12L));

        grant(user, "REPORTS_VIEW", PermissionScope.ORGANIZATION);
        authenticate(user, "REPORTS_VIEW");
        assertTrue(service.isAllowed("REPORTS_VIEW", null, null));
    }

    @Test
    void customScopeIsExcludedFromEffectiveAuthorization() {
        Company company = company(1L);
        User user = user(10L, company);
        grant(user, "EMPLOYEE_VIEW", PermissionScope.CUSTOM);
        authenticate(user, "EMPLOYEE_VIEW");
        TenantContext.setCurrentTenant(1L);

        assertEquals(Set.of(), service.getScopes("EMPLOYEE_VIEW"));
        assertFalse(service.isAllowed("EMPLOYEE_VIEW", null, null));
    }

    @Test
    void organizationScopeHelperRejectsNarrowGrants() {
        Company company = company(1L);
        User user = user(10L, company);
        grant(user, "SALARY_MANAGE", PermissionScope.TEAM);
        authenticate(user, "SALARY_MANAGE");
        TenantContext.setCurrentTenant(1L);

        assertFalse(service.hasOrganizationScope("SALARY_MANAGE"));
    }

    private void authenticate(User user, String permission) {
        when(userRepository.findByUsernameAndDeletedFalse(user.getUsername())).thenReturn(Optional.of(user));
        when(permissionGrantRepository.findAllByCompany_IdAndUser_IdAndRevokedAtIsNullAndDeletedFalse(
                user.getCompany().getId(), user.getId())).thenReturn(java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserPrincipal(user), null, Set.of(() -> permission)));
    }

    private void authenticateWithGrants(User user, String permission, UserPermissionGrant... grants) {
        when(userRepository.findByUsernameAndDeletedFalse(user.getUsername())).thenReturn(Optional.of(user));
        when(permissionGrantRepository.findAllByCompany_IdAndUser_IdAndRevokedAtIsNullAndDeletedFalse(
                user.getCompany().getId(), user.getId())).thenReturn(java.util.List.of(grants));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserPrincipal(user, java.util.List.of(grants)), null, Set.of(() -> permission)));
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

    private UserPermissionGrant userGrant(Company company, User user, String code, PermissionScope scope) {
        Permission permission = new Permission();
        permission.setCode(code);
        UserPermissionGrant grant = new UserPermissionGrant();
        grant.setCompany(company);
        grant.setUser(user);
        grant.setPermission(permission);
        grant.setScope(scope);
        grant.setGrantedBy(user(99L, company));
        grant.setGrantedAt(java.time.LocalDateTime.now());
        return grant;
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

    private Team team(Long id) {
        Team team = new Team();
        team.setId(id);
        return team;
    }
}
