package com.haodaone.user.dto;

import com.haodaone.user.entity.User;
import com.haodaone.user.entity.UserPermissionGrant;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.haodaone.user.entity.PermissionScope;

public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private boolean active;
    private boolean mustChangePassword;
    private LocalDateTime lastLoginAt;
    private List<String> roles;
    private Set<String> permissions;
    private Long employeeId;
    private java.util.Map<String, Set<PermissionScope>> scopes;

    public static UserDTO from(User user) {
        return from(user, List.of());
    }

    public static UserDTO from(User user, Collection<UserPermissionGrant> permissionGrants) {
        UserDTO dto = new UserDTO();
        dto.id = user.getId();
        dto.username = user.getUsername();
        dto.email = user.getEmail();
        dto.fullName = user.getFullName();
        dto.active = user.isActive();
        dto.mustChangePassword = user.isMustChangePassword();
        dto.lastLoginAt = user.getLastLoginAt();
        dto.roles = user.getRoles().stream()
                .map(com.haodaone.user.entity.Role::getName)
                .collect(Collectors.toList());

        dto.permissions = new HashSet<>();
        dto.scopes = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        user.getRoles().stream()
                .flatMap(role -> role.getPermissionScopes().stream())
                .filter(scope -> scope.getPermission() != null && !scope.getPermission().isDeleted())
                .filter(scope -> scope.getScope() != null && scope.getScope() != PermissionScope.CUSTOM)
                .filter(scope -> scope.getValidFrom() == null || !now.isBefore(scope.getValidFrom()))
                .filter(scope -> scope.getValidUntil() == null || now.isBefore(scope.getValidUntil()))
                .forEach(scope -> {
                    String code = scope.getPermission().getCode();
                    dto.permissions.add(code);
                    dto.scopes.computeIfAbsent(code, ignored -> new HashSet<>()).add(scope.getScope());
                });
        Long companyId = user.getCompany() == null ? null : user.getCompany().getId();
        permissionGrants.stream()
                .filter(UserPermissionGrant::isActive)
                .filter(grant -> companyId != null && grant.getCompany() != null
                        && companyId.equals(grant.getCompany().getId())
                        && grant.getUser() != null && user.getId().equals(grant.getUser().getId())
                        && grant.getPermission() != null && !grant.getPermission().isDeleted()
                        && grant.getScope() != null && grant.getScope() != PermissionScope.CUSTOM)
                .forEach(grant -> {
                    String code = grant.getPermission().getCode();
                    dto.permissions.add(code);
                    dto.scopes.computeIfAbsent(code, ignored -> new HashSet<>()).add(grant.getScope());
                });
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public List<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    /** Null for logins with no linked Employee record (see Employee.user javadoc) - not every account is an employee. */
    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public java.util.Map<String, Set<PermissionScope>> getScopes() { return scopes; }
}
