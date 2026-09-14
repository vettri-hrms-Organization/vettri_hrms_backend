package com.haodaone.org.dto;

import com.haodaone.org.entity.Team;
import jakarta.validation.constraints.NotBlank;

public class TeamDTO {
    private Long id;
    private String name;
    private Long departmentId;
    private String departmentName;
    private Long leadEmployeeId;
    private String leadEmployeeName;
    private boolean active;
    private long memberCount;

    public static TeamDTO from(Team t) {
        TeamDTO dto = new TeamDTO();
        dto.id = t.getId();
        dto.name = t.getName();
        dto.active = t.isActive();
        dto.leadEmployeeId = t.getLeadEmployeeId();
        if (t.getDepartment() != null) {
            dto.departmentId = t.getDepartment().getId();
            dto.departmentName = t.getDepartment().getName();
        }
        return dto;
    }

    public void setLeadEmployeeName(String leadEmployeeName) {
        this.leadEmployeeName = leadEmployeeName;
    }

    public void setMemberCount(long memberCount) {
        this.memberCount = memberCount;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public Long getLeadEmployeeId() {
        return leadEmployeeId;
    }

    public String getLeadEmployeeName() {
        return leadEmployeeName;
    }

    public boolean isActive() {
        return active;
    }

    public long getMemberCount() {
        return memberCount;
    }

    public static class CreateRequest {
        @NotBlank(message = "Team name is required")
        private String name;
        private Long departmentId;
        private Long leadEmployeeId;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getDepartmentId() {
            return departmentId;
        }

        public void setDepartmentId(Long departmentId) {
            this.departmentId = departmentId;
        }

        public Long getLeadEmployeeId() {
            return leadEmployeeId;
        }

        public void setLeadEmployeeId(Long leadEmployeeId) {
            this.leadEmployeeId = leadEmployeeId;
        }
    }
}
