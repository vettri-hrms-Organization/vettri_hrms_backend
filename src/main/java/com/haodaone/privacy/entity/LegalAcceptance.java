package com.haodaone.privacy.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One user's acceptance of one legal document version (append-only evidence record). */
@Entity
@Table(name = "legal_acceptance")
public class LegalAcceptance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "doc_type", nullable = false, length = 30)
    private String docType;

    @Column(name = "doc_version", nullable = false, length = 40)
    private String docVersion;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** True for the DPA, which the company admin accepts on behalf of the company (the data fiduciary). */
    @Column(name = "on_behalf_of_company", nullable = false)
    private boolean onBehalfOfCompany = false;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getDocVersion() { return docVersion; }
    public void setDocVersion(String docVersion) { this.docVersion = docVersion; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public boolean isOnBehalfOfCompany() { return onBehalfOfCompany; }
    public void setOnBehalfOfCompany(boolean onBehalfOfCompany) { this.onBehalfOfCompany = onBehalfOfCompany; }
}
