package com.haodaone.audit.entity;

import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Platform-wide audit trail. Deliberately NOT extending BaseEntity - audit
 * rows are themselves immutable history and shouldn't carry soft-delete/
 * versioning semantics (an audit log entry is never edited or "deleted").
 *
 * Every module calls AuditLogService.log(...) from its service layer on
 * create/update/deactivate/delete actions - see UserService/RoleService
 * for the pattern to follow.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. "User", "Role", "Employee" (Phase 1+) */
    @Column(name = "entity_name", nullable = false, length = 100)
    private String entityName;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    /** CREATE / UPDATE / DELETE / ACTIVATE / DEACTIVATE / LOGIN / LOGOUT / PASSWORD_CHANGE etc. */
    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt = LocalDateTime.now();

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /** Human-readable summary of what changed, e.g. "active: true -> false". Kept short by design - full diffs are out of scope for Phase 0. */
    @Column(length = 1000)
    private String details;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public LocalDateTime getPerformedAt() {
        return performedAt;
    }

    public void setPerformedAt(LocalDateTime performedAt) {
        this.performedAt = performedAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
