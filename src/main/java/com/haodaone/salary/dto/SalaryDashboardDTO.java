package com.haodaone.salary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Everything the Salary Dashboard renders in one call. */
public class SalaryDashboardDTO {

    private Overview overview;
    private List<DepartmentAmount> departmentDistribution;
    private List<MonthlyTrendPoint> payrollTrend;
    private List<RecentActivity> recentActivity;

    public Overview getOverview() {
        return overview;
    }

    public void setOverview(Overview overview) {
        this.overview = overview;
    }

    public List<DepartmentAmount> getDepartmentDistribution() {
        return departmentDistribution;
    }

    public void setDepartmentDistribution(List<DepartmentAmount> departmentDistribution) {
        this.departmentDistribution = departmentDistribution;
    }

    public List<MonthlyTrendPoint> getPayrollTrend() {
        return payrollTrend;
    }

    public void setPayrollTrend(List<MonthlyTrendPoint> payrollTrend) {
        this.payrollTrend = payrollTrend;
    }

    public List<RecentActivity> getRecentActivity() {
        return recentActivity;
    }

    public void setRecentActivity(List<RecentActivity> recentActivity) {
        this.recentActivity = recentActivity;
    }

    /** The headline KPI strip. */
    public static class Overview {
        private long activeEmployeesWithSalary;
        private BigDecimal monthlyPayrollCost = BigDecimal.ZERO;
        private BigDecimal averageSalary = BigDecimal.ZERO;
        private BigDecimal highestSalary = BigDecimal.ZERO;
        private BigDecimal lowestSalary = BigDecimal.ZERO;
        private int employeesProcessed;
        private int employeesPending;
        private int payrollCompletionPercent;
        private String currentPeriodLabel;
        private String currentPeriodStatus;
        private LocalDate upcomingPayrollDate;

        public long getActiveEmployeesWithSalary() {
            return activeEmployeesWithSalary;
        }

        public void setActiveEmployeesWithSalary(long activeEmployeesWithSalary) {
            this.activeEmployeesWithSalary = activeEmployeesWithSalary;
        }

        public BigDecimal getMonthlyPayrollCost() {
            return monthlyPayrollCost;
        }

        public void setMonthlyPayrollCost(BigDecimal monthlyPayrollCost) {
            this.monthlyPayrollCost = monthlyPayrollCost;
        }

        public BigDecimal getAverageSalary() {
            return averageSalary;
        }

        public void setAverageSalary(BigDecimal averageSalary) {
            this.averageSalary = averageSalary;
        }

        public BigDecimal getHighestSalary() {
            return highestSalary;
        }

        public void setHighestSalary(BigDecimal highestSalary) {
            this.highestSalary = highestSalary;
        }

        public BigDecimal getLowestSalary() {
            return lowestSalary;
        }

        public void setLowestSalary(BigDecimal lowestSalary) {
            this.lowestSalary = lowestSalary;
        }

        public int getEmployeesProcessed() {
            return employeesProcessed;
        }

        public void setEmployeesProcessed(int employeesProcessed) {
            this.employeesProcessed = employeesProcessed;
        }

        public int getEmployeesPending() {
            return employeesPending;
        }

        public void setEmployeesPending(int employeesPending) {
            this.employeesPending = employeesPending;
        }

        public int getPayrollCompletionPercent() {
            return payrollCompletionPercent;
        }

        public void setPayrollCompletionPercent(int payrollCompletionPercent) {
            this.payrollCompletionPercent = payrollCompletionPercent;
        }

        public String getCurrentPeriodLabel() {
            return currentPeriodLabel;
        }

        public void setCurrentPeriodLabel(String currentPeriodLabel) {
            this.currentPeriodLabel = currentPeriodLabel;
        }

        public String getCurrentPeriodStatus() {
            return currentPeriodStatus;
        }

        public void setCurrentPeriodStatus(String currentPeriodStatus) {
            this.currentPeriodStatus = currentPeriodStatus;
        }

        public LocalDate getUpcomingPayrollDate() {
            return upcomingPayrollDate;
        }

        public void setUpcomingPayrollDate(LocalDate upcomingPayrollDate) {
            this.upcomingPayrollDate = upcomingPayrollDate;
        }
    }

    /** One slice of "Department Salary Distribution" / "Salary Expense by Department". */
    public static class DepartmentAmount {
        private final String departmentName;
        private final long headcount;
        private final BigDecimal totalNetSalary;

        public DepartmentAmount(String departmentName, long headcount, BigDecimal totalNetSalary) {
            this.departmentName = departmentName;
            this.headcount = headcount;
            this.totalNetSalary = totalNetSalary;
        }

        public String getDepartmentName() {
            return departmentName;
        }

        public long getHeadcount() {
            return headcount;
        }

        public BigDecimal getTotalNetSalary() {
            return totalNetSalary;
        }
    }

    /** One point of the "Payroll Trend (Last 12 Months)" chart. */
    public static class MonthlyTrendPoint {
        private final String periodLabel;
        private final int periodMonth;
        private final int periodYear;
        private final BigDecimal totalGross;
        private final BigDecimal totalDeductions;
        private final BigDecimal totalNet;

        public MonthlyTrendPoint(String periodLabel, int periodMonth, int periodYear,
                                  BigDecimal totalGross, BigDecimal totalDeductions, BigDecimal totalNet) {
            this.periodLabel = periodLabel;
            this.periodMonth = periodMonth;
            this.periodYear = periodYear;
            this.totalGross = totalGross;
            this.totalDeductions = totalDeductions;
            this.totalNet = totalNet;
        }

        public String getPeriodLabel() {
            return periodLabel;
        }

        public int getPeriodMonth() {
            return periodMonth;
        }

        public int getPeriodYear() {
            return periodYear;
        }

        public BigDecimal getTotalGross() {
            return totalGross;
        }

        public BigDecimal getTotalDeductions() {
            return totalDeductions;
        }

        public BigDecimal getTotalNet() {
            return totalNet;
        }
    }

    /** One row of "Recent Payroll Activity" - sourced straight from the shared audit log. */
    public static class RecentActivity {
        private final String entityName;
        private final String action;
        private final String details;
        private final String performedBy;
        private final LocalDateTime performedAt;

        public RecentActivity(String entityName, String action, String details, String performedBy, LocalDateTime performedAt) {
            this.entityName = entityName;
            this.action = action;
            this.details = details;
            this.performedBy = performedBy;
            this.performedAt = performedAt;
        }

        public String getEntityName() {
            return entityName;
        }

        public String getAction() {
            return action;
        }

        public String getDetails() {
            return details;
        }

        public String getPerformedBy() {
            return performedBy;
        }

        public LocalDateTime getPerformedAt() {
            return performedAt;
        }
    }
}
