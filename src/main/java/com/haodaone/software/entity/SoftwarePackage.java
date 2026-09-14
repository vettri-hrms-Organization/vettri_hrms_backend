package com.haodaone.software.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

@Entity
@Table(name = "software_package", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"company_id", "name"})
})
public class SoftwarePackage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "publisher", length = 150)
    private String publisher;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private SoftwarePlatform platform = SoftwarePlatform.WINDOWS;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SoftwarePlatform getPlatform() {
        return platform;
    }

    public void setPlatform(SoftwarePlatform platform) {
        this.platform = platform;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
