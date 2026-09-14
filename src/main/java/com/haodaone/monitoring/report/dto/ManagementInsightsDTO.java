package com.haodaone.monitoring.report.dto;

import java.util.List;

/** Powers the Management View: leaderboards and org-wide averages computed over the filtered report window. */
public class ManagementInsightsDTO {

    private List<EmployeeRanking> mostActiveEmployees;
    private List<EmployeeRanking> highestIdleEmployees;
    private List<EmployeeRanking> productivityRanking;
    private double averageWorkingHours;
    private double averageProductivityPercent;
    private int employeeDaysAnalyzed;

    public List<EmployeeRanking> getMostActiveEmployees() {
        return mostActiveEmployees;
    }

    public void setMostActiveEmployees(List<EmployeeRanking> mostActiveEmployees) {
        this.mostActiveEmployees = mostActiveEmployees;
    }

    public List<EmployeeRanking> getHighestIdleEmployees() {
        return highestIdleEmployees;
    }

    public void setHighestIdleEmployees(List<EmployeeRanking> highestIdleEmployees) {
        this.highestIdleEmployees = highestIdleEmployees;
    }

    public List<EmployeeRanking> getProductivityRanking() {
        return productivityRanking;
    }

    public void setProductivityRanking(List<EmployeeRanking> productivityRanking) {
        this.productivityRanking = productivityRanking;
    }

    public double getAverageWorkingHours() {
        return averageWorkingHours;
    }

    public void setAverageWorkingHours(double averageWorkingHours) {
        this.averageWorkingHours = averageWorkingHours;
    }

    public double getAverageProductivityPercent() {
        return averageProductivityPercent;
    }

    public void setAverageProductivityPercent(double averageProductivityPercent) {
        this.averageProductivityPercent = averageProductivityPercent;
    }

    public int getEmployeeDaysAnalyzed() {
        return employeeDaysAnalyzed;
    }

    public void setEmployeeDaysAnalyzed(int employeeDaysAnalyzed) {
        this.employeeDaysAnalyzed = employeeDaysAnalyzed;
    }

    public static class EmployeeRanking {
        private Long employeeId;
        private String employeeCode;
        private String employeeName;
        private String departmentName;
        private double value;

        public EmployeeRanking() {
        }

        public EmployeeRanking(Long employeeId, String employeeCode, String employeeName, String departmentName, double value) {
            this.employeeId = employeeId;
            this.employeeCode = employeeCode;
            this.employeeName = employeeName;
            this.departmentName = departmentName;
            this.value = value;
        }

        public Long getEmployeeId() {
            return employeeId;
        }

        public String getEmployeeCode() {
            return employeeCode;
        }

        public String getEmployeeName() {
            return employeeName;
        }

        public String getDepartmentName() {
            return departmentName;
        }

        public double getValue() {
            return value;
        }
    }
}
