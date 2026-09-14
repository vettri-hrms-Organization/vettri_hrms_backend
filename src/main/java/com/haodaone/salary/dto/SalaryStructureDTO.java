package com.haodaone.salary.dto;

import com.haodaone.salary.entity.SalaryStructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class SalaryStructureDTO {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private LocalDate effectiveFrom;
    private boolean active;
    private SalaryComponentsDTO components;
    private BigDecimal grossSalary;
    private BigDecimal totalDeductions;
    private BigDecimal netSalary;
    private String notes;
    private LocalDateTime createdAt;
    private String createdBy;

    public static SalaryStructureDTO from(SalaryStructure s) {
        SalaryStructureDTO dto = new SalaryStructureDTO();
        dto.id = s.getId();
        dto.employeeId = s.getEmployee().getId();
        dto.employeeName = s.getEmployee().getFullName();
        dto.employeeCode = s.getEmployee().getEmployeeCode();
        dto.effectiveFrom = s.getEffectiveFrom();
        dto.active = s.isActive();
        dto.components = SalaryComponentsDTO.from(s.getComponents());
        dto.grossSalary = s.getGrossSalary();
        dto.totalDeductions = s.getTotalDeductions();
        dto.netSalary = s.getNetSalary();
        dto.notes = s.getNotes();
        dto.createdAt = s.getCreatedAt();
        dto.createdBy = s.getCreatedBy();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public boolean isActive() {
        return active;
    }

    public SalaryComponentsDTO getComponents() {
        return components;
    }

    public BigDecimal getGrossSalary() {
        return grossSalary;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public BigDecimal getNetSalary() {
        return netSalary;
    }

    public String getNotes() {
        return notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
