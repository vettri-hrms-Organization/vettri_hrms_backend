package com.haodaone.user.dto;

import com.haodaone.company.entity.Company;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.RolePermissionScope;
import com.haodaone.user.entity.User;
import com.haodaone.user.entity.UserPermissionGrant;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserDTOEffectivePermissionsTest {

    @Test
    void dtoCombinesRoleAndActiveSameTenantGrantPermissionsAndScopes() {
        Company company = new Company();
        company.setId(1L);
        User user = new User();
        user.setId(10L);
        user.setCompany(company);

        Permission rolePermission = permission("MONITORING_VIEW");
        Role role = new Role();
        role.setName("MANAGER");
        role.setPermissions(Set.of(rolePermission));
        RolePermissionScope roleScope = new RolePermissionScope();
        roleScope.setRole(role);
        roleScope.setPermission(rolePermission);
        roleScope.setScope(PermissionScope.TEAM);
        role.setPermissionScopes(Set.of(roleScope));
        user.setRoles(Set.of(role));

        UserPermissionGrant activeGrant = grant(company, user, permission("RECRUITMENT_MANAGE"), PermissionScope.DEPARTMENT);
        UserPermissionGrant revokedGrant = grant(company, user, permission("SOFTWARE_MANAGE"), PermissionScope.ORGANIZATION);
        revokedGrant.setRevokedAt(LocalDateTime.now());
        Company otherCompany = new Company();
        otherCompany.setId(2L);
        UserPermissionGrant crossTenantGrant = grant(
                otherCompany, user, permission("PAYROLL_VIEW"), PermissionScope.ORGANIZATION);
        UserPermissionGrant customGrant = grant(
                company, user, permission("CUSTOM_PERMISSION"), PermissionScope.CUSTOM);

        UserDTO dto = UserDTO.from(user, Set.of(activeGrant, revokedGrant, crossTenantGrant, customGrant));

        assertEquals(Set.of("MONITORING_VIEW", "RECRUITMENT_MANAGE"), dto.getPermissions());
        assertEquals(Set.of(PermissionScope.TEAM), dto.getScopes().get("MONITORING_VIEW"));
        assertEquals(Set.of(PermissionScope.DEPARTMENT), dto.getScopes().get("RECRUITMENT_MANAGE"));
        assertTrue(dto.getScopes().get("SOFTWARE_MANAGE") == null);
        assertTrue(dto.getScopes().get("CUSTOM_PERMISSION") == null);
    }

    private UserPermissionGrant grant(Company company, User user, Permission permission, PermissionScope scope) {
        UserPermissionGrant grant = new UserPermissionGrant();
        grant.setCompany(company);
        grant.setUser(user);
        grant.setPermission(permission);
        grant.setScope(scope);
        grant.setGrantedAt(LocalDateTime.now());
        return grant;
    }

    private Permission permission(String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setDescription(code);
        return permission;
    }
}
