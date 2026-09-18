package com.haodaone.user.dto;

import com.haodaone.user.entity.Role;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.haodaone.user.entity.PermissionScope;

public class RoleDTO {
    private Long id;
    private String name;
    private String description;
    private boolean systemDefined;
    private List<PermissionDTO> permissions;
    private Long companyId;
    private Map<String, PermissionScope> scopes;

    public static RoleDTO from(Role role) {
        RoleDTO dto = new RoleDTO();
        dto.id = role.getId();
        dto.name = role.getName();
        dto.description = role.getDescription();
        dto.systemDefined = role.isSystemDefined();
        dto.permissions = role.getPermissions().stream().map(PermissionDTO::from).toList();
        dto.companyId = role.getCompany() == null ? null : role.getCompany().getId();
        dto.scopes = role.getPermissionScopes().stream().collect(Collectors.toMap(
            scope -> scope.getPermission().getCode(),
            scope -> scope.getScope(),
            (first, second) -> first));
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystemDefined() {
        return systemDefined;
    }

    public List<PermissionDTO> getPermissions() {
        return permissions;
    }

    public Long getCompanyId() { return companyId; }
    public Map<String, PermissionScope> getScopes() { return scopes; }
}
