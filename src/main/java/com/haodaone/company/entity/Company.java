package com.haodaone.company.entity;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Tenant / Company record for Phase 1 multi-tenant support.
 * Additive and intentionally small for Phase 1: name + optional domain.
 */
@Entity
@Table(name = "company")
public class Company extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "domain", length = 255)
    private String domain;

    @Column(name = "productive_threshold_percent")
    private Integer productiveThresholdPercent = 80;

    @Column(name = "neutral_threshold_percent")
    private Integer neutralThresholdPercent = 50;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Integer getProductiveThresholdPercent() {
        return productiveThresholdPercent;
    }

    public void setProductiveThresholdPercent(Integer value) {
        this.productiveThresholdPercent = value;
    }

    public Integer getNeutralThresholdPercent() {
        return neutralThresholdPercent;
    }

    public void setNeutralThresholdPercent(Integer value) {
        this.neutralThresholdPercent = value;
    }
}
