package com.haodaone.user.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.user.repository.UserRepository;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.dto.CreateRoleRequest;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.repository.PermissionRepository;
import com.haodaone.user.repository.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoleServiceReservedRoleTest {

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
    private final RoleService roleService = new RoleService(
            roleRepository,
            permissionRepository,
            mock(AuditLogService.class),
            mock(CompanyRepository.class),
            mock(AuthorizationService.class),
            mock(UserRepository.class));

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void cannotCreateCustomRoleWithReservedSystemRoleName() {
        TenantContext.setCurrentTenant(7L);
        CreateRoleRequest request = new CreateRoleRequest();
        request.setName(" super_admin ");

        assertThrows(BadRequestException.class, () -> roleService.create(request));
    }

    @Test
    void itManagementPermissionRequiresCompanyScope() {
        TenantContext.setCurrentTenant(7L);
        Permission permission = new Permission();
        permission.setCode("IT_MANAGEMENT_ACCESS");
        when(permissionRepository.findByCode("IT_MANAGEMENT_ACCESS")).thenReturn(java.util.Optional.of(permission));
        CreateRoleRequest request = new CreateRoleRequest();
        request.setName("Narrow IT Role");
        request.setPermissionCodes(Set.of("IT_MANAGEMENT_ACCESS"));
        request.setPermissionScopes(Map.of("IT_MANAGEMENT_ACCESS", PermissionScope.TEAM));

        assertThrows(BadRequestException.class, () -> roleService.create(request));
    }

    @Test
    void customScopeCannotBeAssignedBeforeCustomTargetsAreSupported() {
        TenantContext.setCurrentTenant(7L);
        Permission permission = new Permission();
        permission.setCode("EMPLOYEE_VIEW");
        when(permissionRepository.findByCode("EMPLOYEE_VIEW")).thenReturn(java.util.Optional.of(permission));
        CreateRoleRequest request = new CreateRoleRequest();
        request.setName("Custom scoped role");
        request.setPermissionCodes(Set.of("EMPLOYEE_VIEW"));
        request.setPermissionScopes(Map.of("EMPLOYEE_VIEW", PermissionScope.CUSTOM));

        assertThrows(BadRequestException.class, () -> roleService.create(request));
    }
}
