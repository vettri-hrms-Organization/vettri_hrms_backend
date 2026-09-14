package com.haodaone.security;

import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapts our User entity to Spring Security's UserDetails contract.
 * Authorities include BOTH role names (prefixed ROLE_, for @PreAuthorize
 * hasRole checks) AND permission codes (unprefixed, for hasAuthority
 * checks) - so controllers can guard by either coarse role or fine-grained
 * permission depending on what the endpoint needs.
 */
public class CustomUserPrincipal implements UserDetails {

    private final User user;

    public CustomUserPrincipal(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
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
        for (Role role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
            for (Permission permission : role.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(permission.getCode()));
            }
        }
        return authorities;
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
        return user.getLockedUntil() == null || user.getLockedUntil().isBefore(java.time.LocalDateTime.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
