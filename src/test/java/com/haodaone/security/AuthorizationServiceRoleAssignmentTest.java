package com.haodaone.security;

import com.haodaone.attendance.repository.WfhRequestRepository;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.RolePermissionScope;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserPermissionGrantRepository;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationServiceRoleAssignmentTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserPermissionGrantRepository grantRepository = mock(UserPermissionGrantRepository.class);
    private final AuthorizationService authorizationService = new AuthorizationService(
            userRepository, mock(EmployeeRepository.class), mock(LeaveRequestRepository.class),
            mock(WfhRequestRepository.class), grantRepository);
    private Company company;
    private User actor;
    private User recipient;
    private Role actorRole;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(7L);
        company = new Company();
        company.setId(7L);
        actor = user(10L, "hr-manager");
        recipient = user(11L, "employee");
        actorRole = new Role();
        actorRole.setPermissions(new HashSet<>());
        actorRole.setPermissionScopes(new HashSet<>());
        actor.setRoles(Set.of(actorRole));

        when(userRepository.findByUsernameAndDeletedFalse("hr-manager")).thenReturn(Optional.of(actor));
        when(userRepository.findByIdAndCompanyIdAndDeletedFalse(11L, 7L)).thenReturn(Optional.of(recipient));
        when(grantRepository.findAllByCompany_IdAndUser_IdAndRevokedAtIsNullAndDeletedFalse(7L, 10L))
                .thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "hr-manager", null, "ROLE_HR_ADMIN", "ROLE_ASSIGN", "OFFICE_LOCATION_VIEW"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void organizationScopedAssignerCanAssignRoleContainingPermissionsTheyHold() {
        addActorPermission("ROLE_ASSIGN");
        addActorPermission("OFFICE_LOCATION_VIEW");
        Role targetRole = role("LOCATION_READER", "OFFICE_LOCATION_VIEW", PermissionScope.ORGANIZATION);

        assertTrue(authorizationService.canAssignRoleToUser(targetRole, recipient.getId()));
    }

    @Test
    void assigneeCannotAssignRoleWithPermissionTheyDoNotHold() {
        addActorPermission("ROLE_ASSIGN");
        Role targetRole = role("PAYROLL_ADMIN", "SALARY_MANAGE", PermissionScope.ORGANIZATION);

        assertFalse(authorizationService.canAssignRoleToUser(targetRole, recipient.getId()));
    }

    @Test
    void assigneeCannotDelegateNonDelegableRoleAssignmentPrivilege() {
        addActorPermission("ROLE_ASSIGN");
        Role targetRole = role("ROLE_DELEGATOR", "ROLE_ASSIGN", PermissionScope.ORGANIZATION);

        assertFalse(authorizationService.canAssignRoleToUser(targetRole, recipient.getId()));
    }

    @Test
    void assigneeCannotAssignPlatformOrCrossOrganizationRole() {
        addActorPermission("ROLE_ASSIGN");
        Role platformRole = new Role();
        platformRole.setName("SUPER_ADMIN");
        platformRole.setPermissions(Set.of());
        platformRole.setPermissionScopes(Set.of());
        Role otherTenantRole = new Role();
        otherTenantRole.setName("OTHER_TENANT_ROLE");
        Company otherCompany = new Company();
        otherCompany.setId(8L);
        otherTenantRole.setCompany(otherCompany);
        otherTenantRole.setPermissions(Set.of());
        otherTenantRole.setPermissionScopes(Set.of());

        assertFalse(authorizationService.canAssignRoleToUser(platformRole, recipient.getId()));
        assertFalse(authorizationService.canAssignRoleToUser(otherTenantRole, recipient.getId()));
    }

    @Test
    void assigneeCannotAssignRolesToTheirOwnAccount() {
        addActorPermission("ROLE_ASSIGN");
        Role targetRole = role("LOCATION_READER", "OFFICE_LOCATION_VIEW", PermissionScope.ORGANIZATION);

        assertFalse(authorizationService.canAssignRoleToUser(targetRole, actor.getId()));
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setCompany(company);
        user.setActive(true);
        user.setAccountStatus("ACTIVE");
        return user;
    }

    private void addActorPermission(String code) {
        Permission permission = permission(code);
        actorRole.getPermissions().add(permission);
        RolePermissionScope scope = new RolePermissionScope();
        scope.setRole(actorRole);
        scope.setPermission(permission);
        scope.setScope(PermissionScope.ORGANIZATION);
        actorRole.getPermissionScopes().add(scope);
    }

    private Role role(String name, String code, PermissionScope scope) {
        Role role = new Role();
        role.setName(name);
        role.setCompany(company);
        Permission permission = permission(code);
        role.setPermissions(Set.of(permission));
        RolePermissionScope roleScope = new RolePermissionScope();
        roleScope.setRole(role);
        roleScope.setPermission(permission);
        roleScope.setScope(scope);
        role.setPermissionScopes(Set.of(roleScope));
        return role;
    }

    private Permission permission(String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        return permission;
    }
}
