package com.haodaone.security;

import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.RolePermissionScope;
import com.haodaone.user.entity.User;
import com.haodaone.user.entity.UserPermissionGrant;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.List;

/**
 * Adapts our User entity to Spring Security's UserDetails contract.
 * Authorities include BOTH role names (prefixed ROLE_, for @PreAuthorize
 * hasRole checks) AND permission codes (unprefixed, for hasAuthority
 * checks) - so controllers can guard by either coarse role or fine-grained
 * permission depending on what the endpoint needs.
 */
public class CustomUserPrincipal implements UserDetails {

    private final User user;
    private final List<UserPermissionGrant> permissionGrants;

    public CustomUserPrincipal(User user) {
        this(user, List.of());
    }

    public CustomUserPrincipal(User user, List<UserPermissionGrant> permissionGrants) {
        this.user = user;
        this.permissionGrants = List.copyOf(permissionGrants);
    }

    public User getUser() {
        return user;
    }

    public List<UserPermissionGrant> getPermissionGrants() {
        return permissionGrants;
    }

    public Long getId() {
        return user.getId();
    }

    /** The tenant/company id this user belongs to, if any (nullable for legacy rows). */
    public Long getCompanyId() {
        return user != null && user.getCompany() != null ? user.getCompany().getId() : null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (Role role : user.getRoles()) {
            if (role.isDeleted()) continue;
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
            Set<String> rolePermissionCodes = role.getPermissions().stream()
                    .filter(permission -> !permission.isDeleted())
                    .map(Permission::getCode)
                    .collect(Collectors.toSet());
            role.getPermissionScopes().stream()
                    .filter(scope -> isEffectiveScope(scope, rolePermissionCodes, now))
                    .map(RolePermissionScope::getPermission)
                    .map(Permission::getCode)
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }
        permissionGrants.stream()
                .filter(UserPermissionGrant::isActive)
                .filter(grant -> user.getCompany() != null && grant.getCompany() != null
                        && user.getCompany().getId().equals(grant.getCompany().getId())
                        && grant.getUser() != null && user.getId().equals(grant.getUser().getId())
                        && grant.getPermission() != null && !grant.getPermission().isDeleted()
                        && grant.getScope() != null
                        && grant.getScope() != com.haodaone.user.entity.PermissionScope.CUSTOM)
                .map(grant -> grant.getPermission().getCode())
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return authorities;
    }

    private boolean isEffectiveScope(RolePermissionScope scope, Set<String> rolePermissionCodes, LocalDateTime now) {
        Permission permission = scope.getPermission();
        PermissionScope permissionScope = scope.getScope();
        return permission != null
                && !permission.isDeleted()
                && rolePermissionCodes.contains(permission.getCode())
                && permissionScope != null
                && permissionScope != PermissionScope.CUSTOM
                && (scope.getValidFrom() == null || !now.isBefore(scope.getValidFrom()))
                && (scope.getValidUntil() == null || now.isBefore(scope.getValidUntil()));
    }

    public java.util.List<String> getRoleNames() {
        return user.getRoles().stream().map(Role::getName).collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return "ACTIVE".equalsIgnoreCase(user.getAccountStatus())
            && (user.getLockedUntil() == null || user.getLockedUntil().isBefore(java.time.LocalDateTime.now()));
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive() && "ACTIVE".equalsIgnoreCase(user.getAccountStatus());
    }
}
