package com.haodaone.user.entity;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "role", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Role extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(length = 300)
    private String description;

    /**
     * Human-readable display name. Reuses the shared role table's existing
     * "label" column (already NOT NULL there for HaodaAsset) rather than
     * adding a duplicate. Defaults to name if not set explicitly.
     */
    @Column(nullable = false, length = 100)
    private String label;

    /** System-defined roles (SUPER_ADMIN, HR_ADMIN, MANAGER, EMPLOYEE) can't be deleted or renamed from the UI. */
    @Column(name = "system_defined", nullable = false)
    private boolean systemDefined = false;

    /**
     * EAGER, not LAZY: CustomUserPrincipal.getAuthorities() reads this on
     * every single authenticated request (JwtAuthenticationFilter runs
     * per-request, outside any @Transactional boundary, and
     * loadUserByUsername() isn't transactional either) - so a LAZY
     * collection here throws LazyInitializationException("no Session")
     * on every request, which JwtAuthenticationFilter's catch-all swallows
     * as "not authenticated", silently 401ing every protected endpoint.
     * HaodaAsset's own Role entity already uses EAGER here for this exact
     * reason.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

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

    public String getLabel() {
        return label != null ? label : name;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isSystemDefined() {
        return systemDefined;
    }

    public void setSystemDefined(boolean systemDefined) {
        this.systemDefined = systemDefined;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions;
    }
}
