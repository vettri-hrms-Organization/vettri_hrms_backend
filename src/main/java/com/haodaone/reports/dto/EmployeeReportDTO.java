package com.haodaone.reports.dto;

import java.util.List;
import java.util.Map;

public class EmployeeReportDTO {
    private long totalEmployees;
    private Map<String, Long> byStatus;
    private Map<String, Long> byEmploymentType;
    private List<DepartmentCount> byDepartment;
    private long newJoinersLast30Days;
    private long newJoinersLast90Days;
    private long separationsLast90Days;

    public EmployeeReportDTO(long totalEmployees, Map<String, Long> byStatus, Map<String, Long> byEmploymentType,
                              List<DepartmentCount> byDepartment, long newJoinersLast30Days,
                              long newJoinersLast90Days, long separationsLast90Days) {
        this.totalEmployees = totalEmployees;
        this.byStatus = byStatus;
        this.byEmploymentType = byEmploymentType;
        this.byDepartment = byDepartment;
        this.newJoinersLast30Days = newJoinersLast30Days;
        this.newJoinersLast90Days = newJoinersLast90Days;
        this.separationsLast90Days = separationsLast90Days;
    }

    public long getTotalEmployees() {
        return totalEmployees;
    }

    public Map<String, Long> getByStatus() {
        return byStatus;
    }

    public Map<String, Long> getByEmploymentType() {
        return byEmploymentType;
    }

    public List<DepartmentCount> getByDepartment() {
        return byDepartment;
    }

    public long getNewJoinersLast30Days() {
        return newJoinersLast30Days;
    }

    public long getNewJoinersLast90Days() {
        return newJoinersLast90Days;
    }

    public long getSeparationsLast90Days() {
        return separationsLast90Days;
    }

    public static class DepartmentCount {
        private final Long departmentId;
        private final String departmentName;
        private final long count;

        public DepartmentCount(Long departmentId, String departmentName, long count) {
            this.departmentId = departmentId;
            this.departmentName = departmentName;
            this.count = count;
        }

        public Long getDepartmentId() {
            return departmentId;
        }

        public String getDepartmentName() {
            return departmentName;
        }

        public long getCount() {
            return count;
        }
    }
}
