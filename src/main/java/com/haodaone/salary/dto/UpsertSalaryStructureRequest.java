package com.haodaone.salary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Body for POST /api/salary/structures - defines (or revises) an employee's compensation. */
public class UpsertSalaryStructureRequest {

    @NotNull(message = "Employee is required")
    private Long employeeId;

    @NotNull(message = "Effective date is required")
    private LocalDate effectiveFrom;

    @NotNull(message = "Salary components are required")
    @Valid
    private SalaryComponentsDTO components;

    private String notes;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public SalaryComponentsDTO getComponents() {
        return components;
    }

    public void setComponents(SalaryComponentsDTO components) {
        this.components = components;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
