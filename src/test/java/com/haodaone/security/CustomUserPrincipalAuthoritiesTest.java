package com.haodaone.security;

import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.RolePermissionScope;
import com.haodaone.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomUserPrincipalAuthoritiesTest {

    @Test
    void authoritiesIncludeOnlyActiveRolePermissionsWithSupportedScopes() {
        Permission active = permission("EMPLOYEE_VIEW");
        Permission missingScope = permission("ATTENDANCE_VIEW");
        Permission expired = permission("LEAVE_VIEW");
        Permission custom = permission("MONITORING_VIEW");
        Permission deleted = permission("DEVICE_MANAGE");
        deleted.setDeleted(true);

        Role role = new Role();
        role.setName("HR_ADMIN");
        role.setPermissions(Set.of(active, missingScope, expired, custom, deleted));
        role.setPermissionScopes(Set.of(
                scope(role, active, PermissionScope.ORGANIZATION, null, null),
                scope(role, expired, PermissionScope.ORGANIZATION, null, LocalDateTime.now().minusSeconds(1)),
                scope(role, custom, PermissionScope.CUSTOM, null, null),
                scope(role, deleted, PermissionScope.ORGANIZATION, null, null)));

        User user = new User();
        user.setRoles(Set.of(role));

        Set<String> authorities = new CustomUserPrincipal(user).getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.of("ROLE_HR_ADMIN", "EMPLOYEE_VIEW"), authorities);
    }

    private Permission permission(String code) {
        Permission permission = new Permission();
        permission.setCode(code);
        return permission;
    }

    private RolePermissionScope scope(Role role, Permission permission, PermissionScope value,
                                      LocalDateTime validFrom, LocalDateTime validUntil) {
        RolePermissionScope scope = new RolePermissionScope();
        scope.setRole(role);
        scope.setPermission(permission);
        scope.setScope(value);
        scope.setValidFrom(validFrom);
        scope.setValidUntil(validUntil);
        return scope;
    }
}
