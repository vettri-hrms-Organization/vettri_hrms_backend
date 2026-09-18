package com.haodaone.user.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.user.dto.CreateRoleRequest;
import com.haodaone.user.dto.RoleDTO;
import com.haodaone.user.dto.UpdateRolePermissionsRequest;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.Role;
import com.haodaone.user.repository.PermissionRepository;
import com.haodaone.user.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import com.haodaone.tenant.TenantContext;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.RolePermissionScope;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditLogService auditLogService;
    private final CompanyRepository companyRepository;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository, AuditLogService auditLogService,
                       CompanyRepository companyRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.auditLogService = auditLogService;
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleDTO> listAll() {
        Long companyId = TenantContext.getCurrentTenant();
        return (companyId == null ? roleRepository.findAllByDeletedFalse()
            : roleRepository.findAvailableForCompany(companyId))
                .stream().map(RoleDTO::from).toList();
    }

    @Transactional
    public RoleDTO create(CreateRoleRequest request) {
        Long companyId = requiredTenant();
        if (roleRepository.findByNameAndCompany_Id(request.getName(), companyId).isPresent()) {
            throw new BadRequestException("Role '" + request.getName() + "' already exists");
        }

        Role role = new Role();
        role.setName(request.getName());
        role.setLabel(request.getName());
        role.setDescription(request.getDescription());
        role.setSystemDefined(false);
        companyRepository.findById(companyId).ifPresent(role::setCompany);
        role.setPermissions(resolvePermissions(request.getPermissionCodes()));
        applyScopes(role, request.getPermissionScopes());

        Role saved = roleRepository.save(role);
        auditLogService.log("Role", saved.getId(), "CREATE", "Created role '" + saved.getName() + "'");
        return RoleDTO.from(saved);
    }

    @Transactional
    public RoleDTO updatePermissions(Long roleId, Set<String> permissionCodes) {
        Role role = findTenantRole(roleId);
        if (role.isSystemDefined()) {
            throw new BadRequestException("System-defined roles cannot be modified");
        }
        role.setPermissions(resolvePermissions(permissionCodes));
        applyScopes(role, Map.of());
        Role saved = roleRepository.save(role);
        auditLogService.log("Role", saved.getId(), "UPDATE", "Permissions set to " + permissionCodes);
        return RoleDTO.from(saved);
    }

    @Transactional
    public RoleDTO updatePermissions(Long roleId, UpdateRolePermissionsRequest request) {
        Role role = findTenantRole(roleId);
        if (role.isSystemDefined()) {
            throw new BadRequestException("System-defined roles cannot be modified");
        }
        role.setPermissions(resolvePermissions(request.getPermissionCodes()));
        applyScopes(role, request.getPermissionScopes());
        Role saved = roleRepository.save(role);
        auditLogService.log("Role", saved.getId(), "UPDATE", "Permissions and scopes updated");
        return RoleDTO.from(saved);
    }

    @Transactional
    public void delete(Long roleId) {
        Role role = findTenantRole(roleId);
        if (role.isSystemDefined()) {
            throw new BadRequestException("System-defined roles cannot be deleted");
        }
        role.setDeleted(true);
        roleRepository.save(role);
        auditLogService.log("Role", role.getId(), "DELETE", "Deleted role '" + role.getName() + "'");
    }

    private Set<Permission> resolvePermissions(Set<String> codes) {
        Set<Permission> permissions = new HashSet<>();
        for (String code : codes) {
            permissions.add(permissionRepository.findByCode(code)
                    .orElseThrow(() -> new BadRequestException("Unknown permission code: " + code)));
        }
        return permissions;
    }

    private void applyScopes(Role role, Map<String, PermissionScope> requestedScopes) {
        role.getPermissionScopes().clear();
        for (Permission permission : role.getPermissions()) {
            RolePermissionScope scope = new RolePermissionScope();
            scope.setRole(role);
            scope.setPermission(permission);
            scope.setScope(requestedScopes.getOrDefault(permission.getCode(), PermissionScope.ORGANIZATION));
            role.getPermissionScopes().add(scope);
        }
    }

    private Role findTenantRole(Long roleId) {
        Long companyId = requiredTenant();
        return roleRepository.findByIdAndDeletedFalseAndCompany_Id(roleId, companyId)
                .or(() -> roleRepository.findByIdAndDeletedFalseAndCompany_IdIsNull(roleId))
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
    }

    private Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }
}
