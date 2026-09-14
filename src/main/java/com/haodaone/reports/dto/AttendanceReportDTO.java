package com.haodaone.reports.dto;

import java.time.LocalDate;
import java.util.List;

public class AttendanceReportDTO {
    private LocalDate startDate;
    private LocalDate endDate;
    private long totalPunches;
    private long uniqueEmployeesPunched;
    private long totalActiveEmployees;
    private List<DailyCount> dailyDistinctEmployees;
    private List<DepartmentPunchCount> byDepartment;

    public AttendanceReportDTO(LocalDate startDate, LocalDate endDate, long totalPunches, long uniqueEmployeesPunched,
                                long totalActiveEmployees, List<DailyCount> dailyDistinctEmployees,
                                List<DepartmentPunchCount> byDepartment) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalPunches = totalPunches;
        this.uniqueEmployeesPunched = uniqueEmployeesPunched;
        this.totalActiveEmployees = totalActiveEmployees;
        this.dailyDistinctEmployees = dailyDistinctEmployees;
        this.byDepartment = byDepartment;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public long getTotalPunches() {
        return totalPunches;
    }

    public long getUniqueEmployeesPunched() {
        return uniqueEmployeesPunched;
    }

    public long getTotalActiveEmployees() {
        return totalActiveEmployees;
    }

    public List<DailyCount> getDailyDistinctEmployees() {
        return dailyDistinctEmployees;
    }

    public List<DepartmentPunchCount> getByDepartment() {
        return byDepartment;
    }

    public static class DailyCount {
        private final LocalDate date;
        private final long count;

        public DailyCount(LocalDate date, long count) {
            this.date = date;
            this.count = count;
        }

        public LocalDate getDate() {
            return date;
        }

        public long getCount() {
            return count;
        }
    }

    public static class DepartmentPunchCount {
        private final String departmentName;
        private final long punchCount;

        public DepartmentPunchCount(String departmentName, long punchCount) {
            this.departmentName = departmentName;
            this.punchCount = punchCount;
        }

        public String getDepartmentName() {
            return departmentName;
        }

        public long getPunchCount() {
            return punchCount;
        }
    }
}
