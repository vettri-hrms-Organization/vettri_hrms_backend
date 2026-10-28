package com.haodaone.sso.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

/** Per-company SSO policy. No row = defaults (password and SSO both allowed, no auto-provisioning). */
@Entity
@Table(name = "company_sso_setting")
public class CompanySsoSetting extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    /** Password sign-in disabled for the company's users (except the break-glass admin). */
    @Column(name = "sso_only", nullable = false)
    private boolean ssoOnly = false;

    /** The one user still allowed to sign in with a password when ssoOnly; null = every COMPANY_ADMIN. */
    @Column(name = "break_glass_user_id")
    private Long breakGlassUserId;

    @Column(name = "auto_provision_enabled", nullable = false)
    private boolean autoProvisionEnabled = false;

    /** Comma-separated email domains (e.g. "acme.in,acme.com") eligible for auto-provisioning. */
    @Column(name = "auto_provision_domains", length = 500)
    private String autoProvisionDomains;

    /** Skip Vettri two-factor after SSO (the identity provider's MFA is trusted). */
    @Column(name = "trust_sso_mfa", nullable = false)
    private boolean trustSsoMfa = false;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public boolean isSsoOnly() { return ssoOnly; }
    public void setSsoOnly(boolean ssoOnly) { this.ssoOnly = ssoOnly; }
    public Long getBreakGlassUserId() { return breakGlassUserId; }
    public void setBreakGlassUserId(Long breakGlassUserId) { this.breakGlassUserId = breakGlassUserId; }
    public boolean isAutoProvisionEnabled() { return autoProvisionEnabled; }
    public void setAutoProvisionEnabled(boolean autoProvisionEnabled) { this.autoProvisionEnabled = autoProvisionEnabled; }
    public String getAutoProvisionDomains() { return autoProvisionDomains; }
    public void setAutoProvisionDomains(String autoProvisionDomains) { this.autoProvisionDomains = autoProvisionDomains; }
    public boolean isTrustSsoMfa() { return trustSsoMfa; }
    public void setTrustSsoMfa(boolean trustSsoMfa) { this.trustSsoMfa = trustSsoMfa; }
}
