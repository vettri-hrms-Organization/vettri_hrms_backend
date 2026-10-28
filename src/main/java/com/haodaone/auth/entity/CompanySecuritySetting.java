package com.haodaone.auth.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

/** Company-wide security policy (currently the two-factor policy). No row means defaults. */
@Entity
@Table(name = "company_security_setting")
public class CompanySecuritySetting extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "two_factor_policy", nullable = false, length = 30)
    private TwoFactorPolicy twoFactorPolicy = TwoFactorPolicy.OPTIONAL;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public TwoFactorPolicy getTwoFactorPolicy() { return twoFactorPolicy; }
    public void setTwoFactorPolicy(TwoFactorPolicy twoFactorPolicy) { this.twoFactorPolicy = twoFactorPolicy; }
}
