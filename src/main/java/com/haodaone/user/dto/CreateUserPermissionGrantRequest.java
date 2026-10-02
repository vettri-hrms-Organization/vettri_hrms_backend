package com.haodaone.user.dto;

import com.haodaone.user.entity.PermissionScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateUserPermissionGrantRequest {

    @NotBlank
    private String permissionCode;

    @NotNull
    private PermissionScope scope;

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public PermissionScope getScope() {
        return scope;
    }

    public void setScope(PermissionScope scope) {
        this.scope = scope;
    }
}
