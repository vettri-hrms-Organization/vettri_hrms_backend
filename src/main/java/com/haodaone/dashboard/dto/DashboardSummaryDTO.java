package com.haodaone.dashboard.dto;

import com.haodaone.employee.dto.EmployeeSummaryDTO;

import java.time.LocalDate;
import java.util.List;

public class DashboardSummaryDTO {
    private long totalEmployees;
    private long activeEmployees;
    private long onLeave;
    private long noticePeriod;
    private long resigned;
    private long terminated;
    private List<DepartmentCount> departmentBreakdown;
    private List<EmployeeSummaryDTO> recentJoiners;
    private List<UpcomingBirthday> upcomingBirthdays;

    public DashboardSummaryDTO(long totalEmployees, long activeEmployees, long onLeave, long noticePeriod,
                                long resigned, long terminated, List<DepartmentCount> departmentBreakdown,
                                List<EmployeeSummaryDTO> recentJoiners, List<UpcomingBirthday> upcomingBirthdays) {
        this.totalEmployees = totalEmployees;
        this.activeEmployees = activeEmployees;
        this.onLeave = onLeave;
        this.noticePeriod = noticePeriod;
        this.resigned = resigned;
        this.terminated = terminated;
        this.departmentBreakdown = departmentBreakdown;
        this.recentJoiners = recentJoiners;
        this.upcomingBirthdays = upcomingBirthdays;
    }

    public long getTotalEmployees() {
        return totalEmployees;
    }

    public long getActiveEmployees() {
        return activeEmployees;
    }

    public long getOnLeave() {
        return onLeave;
    }

    public long getNoticePeriod() {
        return noticePeriod;
    }

    public long getResigned() {
        return resigned;
    }

    public long getTerminated() {
        return terminated;
    }

    public List<DepartmentCount> getDepartmentBreakdown() {
        return departmentBreakdown;
    }

    public List<EmployeeSummaryDTO> getRecentJoiners() {
        return recentJoiners;
    }

    public List<UpcomingBirthday> getUpcomingBirthdays() {
        return upcomingBirthdays;
    }

    public static class DepartmentCount {
        private final String departmentName;
        private final long count;

        public DepartmentCount(String departmentName, long count) {
            this.departmentName = departmentName;
            this.count = count;
        }

        public String getDepartmentName() {
            return departmentName;
        }

        public long getCount() {
            return count;
        }
    }

    /** Deliberately carries only what the Birthdays card needs to display -
     *  not the full employee record - even though the caller already has
     *  EMPLOYEE_VIEW and could see the full profile anyway. */
    public static class UpcomingBirthday {
        private final Long employeeId;
        private final String fullName;
        private final LocalDate dateOfBirth;
        private final String profilePhotoUrl;

        public UpcomingBirthday(Long employeeId, String fullName, LocalDate dateOfBirth, String profilePhotoUrl) {
            this.employeeId = employeeId;
            this.fullName = fullName;
            this.dateOfBirth = dateOfBirth;
            this.profilePhotoUrl = profilePhotoUrl;
        }

        public Long getEmployeeId() {
            return employeeId;
        }

        public String getFullName() {
            return fullName;
        }

        public LocalDate getDateOfBirth() {
            return dateOfBirth;
        }

        public String getProfilePhotoUrl() {
            return profilePhotoUrl;
        }
    }
}
