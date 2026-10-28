package com.haodaone.letters.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

/** One row per company: logo, address, signatory and footer printed on every generated letter. */
@Entity
@Table(name = "letterhead_settings")
public class LetterheadSettings extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "logo_s3_key", length = 500)
    private String logoS3Key;

    @Column(name = "logo_content_type", length = 60)
    private String logoContentType;

    @Column(length = 1000)
    private String address;

    @Column(name = "signatory_name", length = 150)
    private String signatoryName;

    @Column(name = "signatory_designation", length = 150)
    private String signatoryDesignation;

    @Column(length = 500)
    private String footer;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getLogoS3Key() { return logoS3Key; }
    public void setLogoS3Key(String logoS3Key) { this.logoS3Key = logoS3Key; }
    public String getLogoContentType() { return logoContentType; }
    public void setLogoContentType(String logoContentType) { this.logoContentType = logoContentType; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getSignatoryName() { return signatoryName; }
    public void setSignatoryName(String signatoryName) { this.signatoryName = signatoryName; }
    public String getSignatoryDesignation() { return signatoryDesignation; }
    public void setSignatoryDesignation(String signatoryDesignation) { this.signatoryDesignation = signatoryDesignation; }
    public String getFooter() { return footer; }
    public void setFooter(String footer) { this.footer = footer; }
}
