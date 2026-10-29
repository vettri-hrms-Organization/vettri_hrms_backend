package com.haodaone.supportaccess;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A company admin's permission for Vettri platform staff to open this company (V56).
 * Without an active grant a SUPER_ADMIN cannot select the company at all; READ_ONLY grants
 * also block every write. Grants expire on their own and can be revoked at any time.
 */
@Entity
@Table(name = "support_access_grant")
public class SupportAccessGrant extends BaseEntity {

    public static final String READ_ONLY = "READ_ONLY";
    public static final String FULL = "FULL";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "granted_by_user_id")
    private Long grantedByUserId;

    @Column(name = "granted_by_name", length = 150)
    private String grantedByName;

    @Column(length = 500)
    private String reason;

    @Column(name = "access_level", nullable = false, length = 20)
    private String accessLevel = READ_ONLY;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by_name", length = 150)
    private String revokedByName;

    public boolean isActiveAt(LocalDateTime now) {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(now) && !isDeleted();
    }

    public boolean isReadOnly() { return !FULL.equals(accessLevel); }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Long getGrantedByUserId() { return grantedByUserId; }
    public void setGrantedByUserId(Long grantedByUserId) { this.grantedByUserId = grantedByUserId; }
    public String getGrantedByName() { return grantedByName; }
    public void setGrantedByName(String grantedByName) { this.grantedByName = grantedByName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedByName() { return revokedByName; }
    public void setRevokedByName(String revokedByName) { this.revokedByName = revokedByName; }
}
