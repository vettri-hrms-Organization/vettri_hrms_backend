package com.haodaone.exit;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/** Per-company exit policy (V59); companies without a row use the defaults. */
@Entity
@Table(name = "exit_settings")
public class ExitSettings extends BaseEntity {

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(name = "notice_period_days", nullable = false)
    private int noticePeriodDays = 30;

    /** When off (or the employee has no reporting manager) resignations go straight to HR. */
    @Column(name = "manager_approval", nullable = false)
    private boolean managerApproval = true;

    @Column(name = "exit_interview", nullable = false)
    private boolean exitInterview = true;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public int getNoticePeriodDays() { return noticePeriodDays; }
    public void setNoticePeriodDays(int noticePeriodDays) { this.noticePeriodDays = noticePeriodDays; }
    public boolean isManagerApproval() { return managerApproval; }
    public void setManagerApproval(boolean managerApproval) { this.managerApproval = managerApproval; }
    public boolean isExitInterview() { return exitInterview; }
    public void setExitInterview(boolean exitInterview) { this.exitInterview = exitInterview; }
}
