package com.haodaone.dashboard.dto;

import com.haodaone.employee.dto.EmployeeSummaryDTO;
import com.haodaone.leave.dto.LeaveRequestDTO;

import java.util.List;

/**
 * Backs the Manager persona's "My Team" dashboard widget - direct reports
 * plus the subset of pending leave requests that belong to them, as
 * opposed to the org-wide Approval Queue that HR/Admin see. See
 * DashboardController#myTeam for how the scoping is resolved.
 */
public class TeamDashboardDTO {
    private final List<EmployeeSummaryDTO> teamMembers;
    private final List<LeaveRequestDTO> pendingApprovals;

    public TeamDashboardDTO(List<EmployeeSummaryDTO> teamMembers, List<LeaveRequestDTO> pendingApprovals) {
        this.teamMembers = teamMembers;
        this.pendingApprovals = pendingApprovals;
    }

    public List<EmployeeSummaryDTO> getTeamMembers() {
        return teamMembers;
    }

    public List<LeaveRequestDTO> getPendingApprovals() {
        return pendingApprovals;
    }
}
