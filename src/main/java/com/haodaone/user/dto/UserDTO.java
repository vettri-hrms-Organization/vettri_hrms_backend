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
    private Set<String> rolePermissions;
    private Set<String> directPermissions;
    private Long employeeId;
    private Long companyId;
    private String companyName;
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
        if (user.getCompany() != null) {
            dto.companyId = user.getCompany().getId();
            // Only read the name if it's already loaded; callers outside a session set it explicitly.
            if (org.hibernate.Hibernate.isInitialized(user.getCompany())) {
                dto.companyName = user.getCompany().getName();
            }
        }
        dto.roles = user.getRoles().stream()
                .filter(role -> !role.isDeleted())
                .map(com.haodaone.user.entity.Role::getName)
                .collect(Collectors.toList());

        dto.permissions = new HashSet<>();
        dto.rolePermissions = new HashSet<>();
        dto.directPermissions = new HashSet<>();
        dto.scopes = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        user.getRoles().stream()
                .filter(role -> !role.isDeleted())
                .flatMap(role -> role.getPermissionScopes().stream())
                .filter(scope -> scope.getPermission() != null && !scope.getPermission().isDeleted())
                .filter(scope -> scope.getRole() != null
                        && scope.getRole().getPermissions().stream()
                                .anyMatch(permission -> scope.getPermission().getCode().equals(permission.getCode())
                                        && !permission.isDeleted()))
                .filter(scope -> scope.getScope() != null && scope.getScope() != PermissionScope.CUSTOM)
                .filter(scope -> scope.getValidFrom() == null || !now.isBefore(scope.getValidFrom()))
                .filter(scope -> scope.getValidUntil() == null || now.isBefore(scope.getValidUntil()))
                .forEach(scope -> {
                    String code = scope.getPermission().getCode();
                    dto.permissions.add(code);
                    dto.rolePermissions.add(code);
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
                    dto.directPermissions.add(code);
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

    public Set<String> getRolePermissions() { return rolePermissions; }

    public Set<String> getDirectPermissions() { return directPermissions; }

    /** Null for logins with no linked Employee record (see Employee.user javadoc) - not every account is an employee. */
    public Long getCompanyId() {
        return companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    /** Two-factor state, filled by auth.service.MfaService#applyTo (login, refresh, /me). */
    private boolean mfaEnabled;
    private boolean mfaEnrollmentRequired;

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public boolean isMfaEnrollmentRequired() {
        return mfaEnrollmentRequired;
    }

    public void setMfaEnrollmentRequired(boolean mfaEnrollmentRequired) {
        this.mfaEnrollmentRequired = mfaEnrollmentRequired;
    }

    /** Legal acceptance gate, filled by privacy.service.LegalAcceptanceService#applyTo (login, refresh, /me). */
    private boolean legalAcceptanceRequired;
    private List<String> pendingLegalDocuments = List.of();

    public boolean isLegalAcceptanceRequired() {
        return legalAcceptanceRequired;
    }

    public void setLegalAcceptanceRequired(boolean legalAcceptanceRequired) {
        this.legalAcceptanceRequired = legalAcceptanceRequired;
    }

    public List<String> getPendingLegalDocuments() {
        return pendingLegalDocuments;
    }

    public void setPendingLegalDocuments(List<String> pendingLegalDocuments) {
        this.pendingLegalDocuments = pendingLegalDocuments == null ? List.of() : pendingLegalDocuments;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public java.util.Map<String, Set<PermissionScope>> getScopes() { return scopes; }
}
