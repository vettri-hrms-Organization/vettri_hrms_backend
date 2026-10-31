package com.haodaone.probation;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/** Company probation policy (V67): default length and how early the confirmation review opens. */
@Entity
@Table(name = "probation_settings")
public class ProbationSettings extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "default_months", nullable = false)
    private int defaultMonths = 6;

    @Column(name = "review_window_days", nullable = false)
    private int reviewWindowDays = 15;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getDefaultMonths() { return defaultMonths; }
    public void setDefaultMonths(int defaultMonths) { this.defaultMonths = defaultMonths; }
    public int getReviewWindowDays() { return reviewWindowDays; }
    public void setReviewWindowDays(int reviewWindowDays) { this.reviewWindowDays = reviewWindowDays; }
}
