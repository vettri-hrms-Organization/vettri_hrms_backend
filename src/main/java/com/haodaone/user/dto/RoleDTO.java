package com.haodaone.user.dto;

import com.haodaone.user.entity.Role;

import java.util.List;

public class RoleDTO {
    private Long id;
    private String name;
    private String description;
    private boolean systemDefined;
    private List<PermissionDTO> permissions;

    public static RoleDTO from(Role role) {
        RoleDTO dto = new RoleDTO();
        dto.id = role.getId();
        dto.name = role.getName();
        dto.description = role.getDescription();
        dto.systemDefined = role.isSystemDefined();
        dto.permissions = role.getPermissions().stream().map(PermissionDTO::from).toList();
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
}
