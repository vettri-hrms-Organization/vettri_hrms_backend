package com.haodaone.security;

import com.haodaone.company.entity.Company;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.attendance.repository.WfhRequestRepository;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.RolePermissionScope;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserPermissionGrantRepository;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationServiceOfficeLocationTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserPermissionGrantRepository grantRepository = mock(UserPermissionGrantRepository.class);
    private final AuthorizationService authorizationService = new AuthorizationService(
            userRepository, mock(EmployeeRepository.class), mock(LeaveRequestRepository.class),
            mock(WfhRequestRepository.class), grantRepository);

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void organizationManageScopeAllowsOfficeLocationManagement() {
        authenticateWithPermission("ORG_MANAGE", PermissionScope.ORGANIZATION);

        assertTrue(authorizationService.canManageOfficeLocations());
    }

    @Test
    void attendanceManageOrganizationScopePreservesExistingOfficeManagementAccess() {
        authenticateWithPermission("ATTENDANCE_MANAGE", PermissionScope.ORGANIZATION);

        assertTrue(authorizationService.canManageOfficeLocations());
    }

    @Test
    void nonOrganizationAttendanceScopeCannotManageCompanyOfficeLocations() {
        authenticateWithPermission("ATTENDANCE_MANAGE", PermissionScope.TEAM);

        assertFalse(authorizationService.canManageOfficeLocations());
    }

    private void authenticateWithPermission(String code, PermissionScope scope) {
        TenantContext.setCurrentTenant(7L);
        Company company = new Company();
        company.setId(7L);
        User user = new User();
        user.setId(9L);
        user.setUsername("admin");
        user.setCompany(company);
        user.setActive(true);
        user.setAccountStatus("ACTIVE");

        Permission permission = new Permission();
        permission.setCode(code);
        Role role = new Role();
        role.setPermissions(Set.of(permission));
        RolePermissionScope roleScope = new RolePermissionScope();
        roleScope.setRole(role);
        roleScope.setPermission(permission);
        roleScope.setScope(scope);
        role.setPermissionScopes(Set.of(roleScope));
        user.setRoles(Set.of(role));

        when(userRepository.findByUsernameAndDeletedFalse("admin")).thenReturn(Optional.of(user));
        when(grantRepository.findAllByCompany_IdAndUser_IdAndRevokedAtIsNullAndDeletedFalse(7L, 9L))
                .thenReturn(java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", null, code));
    }
}
