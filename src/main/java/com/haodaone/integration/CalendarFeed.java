package com.haodaone.integration;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A personal ICS calendar subscription link (V68): looked up by token hash; the token is kept encrypted so the link can be shown again. */
@Entity
@Table(name = "calendar_feed")
public class CalendarFeed extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "token_enc", nullable = false, columnDefinition = "TEXT")
    private String tokenEnc;

    @Column(name = "last_fetched_at")
    private LocalDateTime lastFetchedAt;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getTokenEnc() { return tokenEnc; }
    public void setTokenEnc(String tokenEnc) { this.tokenEnc = tokenEnc; }
    public LocalDateTime getLastFetchedAt() { return lastFetchedAt; }
    public void setLastFetchedAt(LocalDateTime lastFetchedAt) { this.lastFetchedAt = lastFetchedAt; }
}
