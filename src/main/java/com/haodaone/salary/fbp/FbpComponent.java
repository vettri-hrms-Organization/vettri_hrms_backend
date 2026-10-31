package com.haodaone.salary.fbp;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

/** A flexible benefit component with an annual limit (V65). */
@Entity
@Table(name = "fbp_component")
public class FbpComponent extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "annual_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal annualLimit;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAnnualLimit() { return annualLimit; }
    public void setAnnualLimit(BigDecimal annualLimit) { this.annualLimit = annualLimit; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
