package com.haodaone.user.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public class CreateRoleRequest {

    @NotBlank(message = "Role name is required")
    private String name;

    private String description;

    /** Permission codes to attach, e.g. ["EMPLOYEE_VIEW", "ATTENDANCE_APPROVE"]. */
    private Set<String> permissionCodes = Set.of();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<String> getPermissionCodes() {
        return permissionCodes;
    }

    public void setPermissionCodes(Set<String> permissionCodes) {
        this.permissionCodes = permissionCodes;
    }
}
