package com.haodaone.user.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.org.entity.Team;
import com.haodaone.org.entity.Department;
import com.haodaone.security.AuthorizationService;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.dto.CreateUserPermissionGrantRequest;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.RolePermissionScope;
import com.haodaone.user.entity.User;
import com.haodaone.user.entity.UserPermissionGrant;
import com.haodaone.user.repository.PermissionRepository;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserPermissionGrantRepository;
import com.haodaone.user.repository.UserRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.attendance.repository.WfhRequestRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPermissionGrantServiceTest {

    private final UserPermissionGrantRepository grantRepository = mock(UserPermissionGrantRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final AuthorizationService authorizationService = new AuthorizationService(
            userRepository, employeeRepository, mock(LeaveRequestRepository.class), mock(WfhRequestRepository.class),
            grantRepository);
    private final UserPermissionGrantService service = new UserPermissionGrantService(
            grantRepository, userRepository, permissionRepository, companyRepository, authorizationService, auditLogService);

    private Company company;
    private User grantor;
    private User recipient;
    private Employee grantorEmployee;
    private Employee recipientEmployee;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
        grantor = user(10L, "grantor");
        recipient = user(11L, "recipient");
        grantorEmployee = employee(grantor, 20L);
        recipientEmployee = employee(recipient, 20L);
        when(userRepository.findByUsernameAndDeletedFalse("grantor")).thenReturn(Optional.of(grantor));
        when(userRepository.findByIdAndCompanyIdAndDeletedFalse(11L, 1L)).thenReturn(Optional.of(recipient));
        when(employeeRepository.findByUser_IdAndDeletedFalse(10L)).thenReturn(Optional.of(grantorEmployee));
        when(employeeRepository.findByUser_IdAndDeletedFalse(11L)).thenReturn(Optional.of(recipientEmployee));
        when(grantRepository.findAllByCompany_IdAndUser_IdAndRevokedAtIsNullAndDeletedFalse(1L, 10L))
                .thenReturn(List.of());
        when(grantRepository.existsByCompany_IdAndUser_IdAndPermission_CodeAndRevokedAtIsNullAndDeletedFalse(
                1L, 11L, "MONITORING_VIEW")).thenReturn(false);
        when(permissionRepository.findByCode("MONITORING_VIEW"))
                .thenReturn(Optional.of(permission("MONITORING_VIEW")));
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(grantRepository.saveAndFlush(any(UserPermissionGrant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        authenticateWithGrantAuthority();
        TenantContext.setCurrentTenant(1L);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void grantsPermissionAtSameTeamScopeAndWritesAuditEvent() {
        addRolePermission(grantor, "USER_PERMISSION_GRANT", PermissionScope.ORGANIZATION);
        addRolePermission(grantor, "MONITORING_VIEW", PermissionScope.TEAM);
        authenticateWithGrantAuthorityAndMonitoring();

        var created = service.grant(11L, request("MONITORING_VIEW", PermissionScope.TEAM));

        assertEquals("MONITORING_VIEW", created.getPermissionCode());
        assertEquals(PermissionScope.TEAM, created.getScope());
        verify(auditLogService).log("UserPermissionGrant", null, "GRANT",
                "Granted MONITORING_VIEW with TEAM scope to user 11");
    }

    @Test
    void grantorCannotWidenTeamPermissionToOrganization() {
        addRolePermission(grantor, "USER_PERMISSION_GRANT", PermissionScope.ORGANIZATION);
        addRolePermission(grantor, "MONITORING_VIEW", PermissionScope.TEAM);
        authenticateWithGrantAuthorityAndMonitoring();

        assertThrows(AccessDeniedException.class,
                () -> service.grant(11L, request("MONITORING_VIEW", PermissionScope.ORGANIZATION)));
        verify(grantRepository, never()).saveAndFlush(any(UserPermissionGrant.class));
    }

    @Test
    void grantorCannotWidenDepartmentPermissionToOrganization() {
        addRolePermission(grantor, "USER_PERMISSION_GRANT", PermissionScope.ORGANIZATION);
        addRolePermission(grantor, "RECRUITMENT_MANAGE", PermissionScope.DEPARTMENT);
        authenticateWithPermissions("USER_PERMISSION_GRANT", "RECRUITMENT_MANAGE");
        when(permissionRepository.findByCode("RECRUITMENT_MANAGE"))
                .thenReturn(Optional.of(permission("RECRUITMENT_MANAGE")));
        Department department = new Department();
        department.setId(30L);
        grantorEmployee.setDepartment(department);
        recipientEmployee.setDepartment(department);

        assertThrows(AccessDeniedException.class,
                () -> service.grant(11L, request("RECRUITMENT_MANAGE", PermissionScope.ORGANIZATION)));
        verify(grantRepository, never()).saveAndFlush(any(UserPermissionGrant.class));
    }

    @Test
    void departmentGrantCannotDelegateTeamContainingMembersOutsideDepartment() {
        addRolePermission(grantor, "USER_PERMISSION_GRANT", PermissionScope.ORGANIZATION);
        addRolePermission(grantor, "RECRUITMENT_MANAGE", PermissionScope.DEPARTMENT);
        authenticateWithPermissions("USER_PERMISSION_GRANT", "RECRUITMENT_MANAGE");
        when(permissionRepository.findByCode("RECRUITMENT_MANAGE"))
                .thenReturn(Optional.of(permission("RECRUITMENT_MANAGE")));
        Department department = new Department();
        department.setId(30L);
        grantorEmployee.setDepartment(department);
        recipientEmployee.setDepartment(department);
        recipientEmployee.setTeam(new Team());
        recipientEmployee.getTeam().setId(20L);
        Employee outsideDepartment = employee(user(12L, "outside"), 20L);
        Department otherDepartment = new Department();
        otherDepartment.setId(31L);
        outsideDepartment.setDepartment(otherDepartment);
        when(employeeRepository.findIdsByCompanyAndTeam(1L, 20L)).thenReturn(List.of(11L, 12L));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(11L, 1L))
                .thenReturn(Optional.of(recipientEmployee));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(12L, 1L))
                .thenReturn(Optional.of(outsideDepartment));

        assertThrows(AccessDeniedException.class,
                () -> service.grant(11L, request("RECRUITMENT_MANAGE", PermissionScope.TEAM)));
        verify(grantRepository, never()).saveAndFlush(any(UserPermissionGrant.class));
    }

    @Test
    void grantorWithGrantManagementButWithoutDelegatedPermissionIsDenied() {
        addRolePermission(grantor, "USER_PERMISSION_GRANT", PermissionScope.ORGANIZATION);
        authenticateWithPermissions("USER_PERMISSION_GRANT");

        assertThrows(AccessDeniedException.class,
                () -> service.grant(11L, request("MONITORING_VIEW", PermissionScope.TEAM)));
        verify(grantRepository, never()).saveAndFlush(any(UserPermissionGrant.class));
    }

    @Test
    void organizationAuthorityAllowsOrganizationGrant() {
        addRolePermission(grantor, "USER_PERMISSION_GRANT", PermissionScope.ORGANIZATION);
        addRolePermission(grantor, "IT_DEVICE_VIEW", PermissionScope.ORGANIZATION);
        authenticateWithPermissions("USER_PERMISSION_GRANT", "IT_DEVICE_VIEW");
        when(permissionRepository.findByCode("IT_DEVICE_VIEW"))
                .thenReturn(Optional.of(permission("IT_DEVICE_VIEW")));

        var created = service.grant(11L, request("IT_DEVICE_VIEW", PermissionScope.ORGANIZATION));

        assertEquals(PermissionScope.ORGANIZATION, created.getScope());
    }

    @Test
    void inactiveRecipientCannotReceiveGrant() {
        recipient.setActive(false);
        addRolePermission(grantor, "USER_PERMISSION_GRANT", PermissionScope.ORGANIZATION);

        assertThrows(BadRequestException.class,
                () -> service.grant(11L, request("MONITORING_VIEW", PermissionScope.TEAM)));
        verify(grantRepository, never()).saveAndFlush(any(UserPermissionGrant.class));
    }

    @Test
    void customScopeIsRejectedUntilTargetsCanBeResolved() {
        addRolePermission(grantor, "USER_PERMISSION_GRANT", PermissionScope.ORGANIZATION);

        assertThrows(BadRequestException.class,
                () -> service.grant(11L, request("MONITORING_VIEW", PermissionScope.CUSTOM)));
        verify(grantRepository, never()).saveAndFlush(any(UserPermissionGrant.class));
    }

    @Test
    void selfGrantIsDenied() {
        addRolePermission(grantor, "USER_PERMISSION_GRANT", PermissionScope.ORGANIZATION);
        assertThrows(AccessDeniedException.class,
                () -> service.grant(10L, request("MONITORING_VIEW", PermissionScope.TEAM)));
    }

    @Test
    void grantorWithoutManagementPermissionIsDenied() {
        addRolePermission(grantor, "MONITORING_VIEW", PermissionScope.TEAM);
        authenticateWithPermissions("MONITORING_VIEW", "USER_PERMISSION_GRANT");

        assertThrows(AccessDeniedException.class,
                () -> service.grant(11L, request("MONITORING_VIEW", PermissionScope.TEAM)));
    }

    @Test
    void organizationAdministrationPermissionsCannotBeGrantedAtNarrowerScope() {
        addRolePermission(grantor, "USER_PERMISSION_GRANT", PermissionScope.ORGANIZATION);
        addRolePermission(grantor, "USER_MANAGE", PermissionScope.ORGANIZATION);
        authenticateWithPermissions("USER_PERMISSION_GRANT", "USER_MANAGE");
        when(permissionRepository.findByCode("USER_MANAGE")).thenReturn(Optional.of(permission("USER_MANAGE")));

        assertThrows(BadRequestException.class,
                () -> service.grant(11L, request("USER_MANAGE", PermissionScope.TEAM)));
        verify(grantRepository, never()).saveAndFlush(any(UserPermissionGrant.class));
    }

    @Test
    void crossTenantRecipientIsNotFound() {
        when(userRepository.findByIdAndCompanyIdAndDeletedFalse(11L, 1L)).thenReturn(Optional.empty());
        addRolePermission(grantor, "USER_PERMISSION_GRANT", PermissionScope.ORGANIZATION);

        assertThrows(com.haodaone.common.exception.ResourceNotFoundException.class,
                () -> service.grant(11L, request("MONITORING_VIEW", PermissionScope.TEAM)));
    }

    @Test
    void duplicateActiveGrantIsRejected() {
        addRolePermission(grantor, "USER_PERMISSION_GRANT", PermissionScope.ORGANIZATION);
        addRolePermission(grantor, "MONITORING_VIEW", PermissionScope.ORGANIZATION);
        authenticateWithPermissions("USER_PERMISSION_GRANT", "MONITORING_VIEW");
        when(grantRepository.existsByCompany_IdAndUser_IdAndPermission_CodeAndRevokedAtIsNullAndDeletedFalse(
                1L, 11L, "MONITORING_VIEW")).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> service.grant(11L, request("MONITORING_VIEW", PermissionScope.TEAM)));
        verify(grantRepository, never()).saveAndFlush(any(UserPermissionGrant.class));
    }

    @Test
    void revokedGrantRetainsRevocationActorAndTimestamp() {
        addRolePermission(grantor, "USER_PERMISSION_GRANT", PermissionScope.ORGANIZATION);
        UserPermissionGrant existing = new UserPermissionGrant();
        existing.setId(99L);
        existing.setCompany(company);
        existing.setUser(recipient);
        existing.setPermission(permission("MONITORING_VIEW"));
        existing.setScope(PermissionScope.TEAM);
        existing.setGrantedBy(grantor);
        existing.setGrantedAt(LocalDateTime.now().minusDays(1));
        when(grantRepository.findByIdAndCompany_IdAndUser_IdAndDeletedFalse(99L, 1L, 11L))
                .thenReturn(Optional.of(existing));
        when(grantRepository.save(existing)).thenReturn(existing);

        var revoked = service.revoke(11L, 99L);

        assertEquals(false, revoked.isActive());
        assertEquals(10L, revoked.getRevokedByUserId());
        assertNotNull(revoked.getRevokedAt());
        verify(auditLogService).log("UserPermissionGrant", 99L, "REVOKE",
                "Revoked MONITORING_VIEW grant from user 11");
    }

    private void authenticateWithGrantAuthority() {
        authenticateWithPermissions("USER_PERMISSION_GRANT");
    }

    private void authenticateWithGrantAuthorityAndMonitoring() {
        authenticateWithPermissions("USER_PERMISSION_GRANT", "MONITORING_VIEW");
    }

    private void authenticateWithPermissions(String... permissions) {
        when(userRepository.findByUsernameAndDeletedFalse("grantor")).thenReturn(Optional.of(grantor));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserPrincipal(grantor), null,
                java.util.Arrays.stream(permissions).map(code -> (org.springframework.security.core.GrantedAuthority) () -> code).toList()));
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setFullName(username);
        user.setActive(true);
        user.setAccountStatus("ACTIVE");
        user.setCompany(company);
        return user;
    }

    private Employee employee(User user, Long teamId) {
        Employee employee = new Employee();
        employee.setId(user.getId());
        employee.setUser(user);
        employee.setCompany(company);
        Team team = new Team();
        team.setId(teamId);
        employee.setTeam(team);
        return employee;
    }

    private Permission permission(String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setDescription(code);
        return permission;
    }

    private CreateUserPermissionGrantRequest request(String code, PermissionScope scope) {
        CreateUserPermissionGrantRequest request = new CreateUserPermissionGrantRequest();
        request.setPermissionCode(code);
        request.setScope(scope);
        return request;
    }

    private void addRolePermission(User user, String code, PermissionScope scope) {
        Permission permission = permission(code);
        Role role = new Role();
        role.setName("test-role");
        role.setPermissions(Set.of(permission));
        RolePermissionScope rolePermissionScope = new RolePermissionScope();
        rolePermissionScope.setRole(role);
        rolePermissionScope.setPermission(permission);
        rolePermissionScope.setScope(scope);
        role.setPermissionScopes(Set.of(rolePermissionScope));
        Set<Role> roles = user.getRoles().isEmpty() ? new java.util.HashSet<>() : new java.util.HashSet<>(user.getRoles());
        roles.add(role);
        user.setRoles(roles);
    }
}
