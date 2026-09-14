package com.haodaone.user.entity;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/**
 * The atomic unit of access control. Roles are just named bundles of these.
 * Code convention: MODULE_ACTION, e.g. EMPLOYEE_CREATE, ATTENDANCE_APPROVE,
 * PAYROLL_VIEW - every new module registers its own permission codes here
 * (via a seeder) rather than hardcoding role checks in controllers.
 */
@Entity
@Table(name = "permission", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class Permission extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    /** Reuses the shared permission table's existing "label" column instead of adding a duplicate "description". */
    @Column(name = "label", nullable = false, length = 150)
    private String description;

    /** Groups permissions in the Settings > Permissions UI, e.g. "User Management", "Attendance". */
    @Column(nullable = false, length = 60)
    private String module;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }
}
