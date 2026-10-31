package com.haodaone.workflow;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

/** A company's multi-level approval chain for one module (V67), e.g. LEAVE: reporting manager -> skip-level manager -> HR. */
@Entity
@Table(name = "approval_workflow")
public class ApprovalWorkflow extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "module", nullable = false, length = 20)
    private String module;

    @Column(name = "levels_json", nullable = false, columnDefinition = "TEXT")
    private String levelsJson;

    @Column(name = "full_chain_min_days", precision = 5, scale = 1)
    private BigDecimal fullChainMinDays;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getLevelsJson() { return levelsJson; }
    public void setLevelsJson(String levelsJson) { this.levelsJson = levelsJson; }
    public BigDecimal getFullChainMinDays() { return fullChainMinDays; }
    public void setFullChainMinDays(BigDecimal fullChainMinDays) { this.fullChainMinDays = fullChainMinDays; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
