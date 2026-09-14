package com.haodaone.user.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.user.dto.CreateRoleRequest;
import com.haodaone.user.dto.RoleDTO;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.Role;
import com.haodaone.user.repository.PermissionRepository;
import com.haodaone.user.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditLogService auditLogService;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository, AuditLogService auditLogService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.auditLogService = auditLogService;
    }

    public List<RoleDTO> listAll() {
        return roleRepository.findAllByDeletedFalse().stream().map(RoleDTO::from).toList();
    }

    @Transactional
    public RoleDTO create(CreateRoleRequest request) {
        if (roleRepository.findByName(request.getName()).isPresent()) {
            throw new BadRequestException("Role '" + request.getName() + "' already exists");
        }

        Role role = new Role();
        role.setName(request.getName());
        role.setLabel(request.getName());
        role.setDescription(request.getDescription());
        role.setSystemDefined(false);
        role.setPermissions(resolvePermissions(request.getPermissionCodes()));

        Role saved = roleRepository.save(role);
        auditLogService.log("Role", saved.getId(), "CREATE", "Created role '" + saved.getName() + "'");
        return RoleDTO.from(saved);
    }

    @Transactional
    public RoleDTO updatePermissions(Long roleId, Set<String> permissionCodes) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));

        role.setPermissions(resolvePermissions(permissionCodes));
        Role saved = roleRepository.save(role);
        auditLogService.log("Role", saved.getId(), "UPDATE", "Permissions set to " + permissionCodes);
        return RoleDTO.from(saved);
    }

    @Transactional
    public void delete(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
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
}
