package com.haodaone.user.dto;

import com.haodaone.user.entity.PermissionScope;

import java.util.Map;
import java.util.Set;

public class UpdateRolePermissionsRequest {
    private Set<String> permissionCodes = Set.of();
    private Map<String, PermissionScope> permissionScopes = Map.of();

    public Set<String> getPermissionCodes() { return permissionCodes; }
    public void setPermissionCodes(Set<String> permissionCodes) { this.permissionCodes = permissionCodes; }
    public Map<String, PermissionScope> getPermissionScopes() { return permissionScopes; }
    public void setPermissionScopes(Map<String, PermissionScope> permissionScopes) { this.permissionScopes = permissionScopes; }
}
