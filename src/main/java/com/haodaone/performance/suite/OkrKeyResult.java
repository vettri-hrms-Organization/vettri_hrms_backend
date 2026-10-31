package com.haodaone.performance.suite;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

/** A measurable key result of an objective (V66). */
@Entity
@Table(name = "okr_key_result")
public class OkrKeyResult extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "objective_id", nullable = false)
    private Long objectiveId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "start_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal startValue = BigDecimal.ZERO;

    @Column(name = "target_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "current_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal currentValue = BigDecimal.ZERO;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "progress", nullable = false, precision = 5, scale = 1)
    private BigDecimal progress = BigDecimal.ZERO;

    @Column(name = "last_note", length = 500)
    private String lastNote;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getObjectiveId() { return objectiveId; }
    public void setObjectiveId(Long objectiveId) { this.objectiveId = objectiveId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public BigDecimal getStartValue() { return startValue; }
    public void setStartValue(BigDecimal startValue) { this.startValue = startValue; }
    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getProgress() { return progress; }
    public void setProgress(BigDecimal progress) { this.progress = progress; }
    public String getLastNote() { return lastNote; }
    public void setLastNote(String lastNote) { this.lastNote = lastNote; }
}
