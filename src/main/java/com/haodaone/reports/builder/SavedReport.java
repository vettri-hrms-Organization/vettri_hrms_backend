package com.haodaone.reports.builder;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/** A saved custom report definition (V68): dataset, columns, filters, grouping. */
@Entity
@Table(name = "saved_report")
public class SavedReport extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "dataset", nullable = false, length = 30)
    private String dataset;

    @Column(name = "definition_json", nullable = false, columnDefinition = "TEXT")
    private String definitionJson;

    @Column(name = "created_by_name", length = 150)
    private String createdByName;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "shared", nullable = false)
    private boolean shared;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDataset() { return dataset; }
    public void setDataset(String dataset) { this.dataset = dataset; }
    public String getDefinitionJson() { return definitionJson; }
    public void setDefinitionJson(String definitionJson) { this.definitionJson = definitionJson; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public boolean isShared() { return shared; }
    public void setShared(boolean shared) { this.shared = shared; }
}
