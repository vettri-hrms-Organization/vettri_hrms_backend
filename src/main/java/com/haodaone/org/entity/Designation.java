package com.haodaone.org.entity;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/** A job title/grade, e.g. "Senior Software Engineer" at level 4. Optionally scoped to a department. */
@Entity
@Table(name = "designation")
public class Designation extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String title;

    /** Seniority/grade level - purely for sorting and org-chart visuals, not tied to any pay logic (out of scope here). */
    @Column
    private Integer level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false)
    private boolean active = true;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
