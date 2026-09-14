package com.haodaone.org.dto;

import com.haodaone.org.entity.Department;
import jakarta.validation.constraints.NotBlank;

public class DepartmentDTO {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Long parentDepartmentId;
    private String parentDepartmentName;
    private Long headEmployeeId;
    private String headEmployeeName;
    private boolean active;
    private long employeeCount;

    public static DepartmentDTO from(Department d) {
        DepartmentDTO dto = new DepartmentDTO();
        dto.id = d.getId();
        dto.name = d.getName();
        dto.code = d.getCode();
        dto.description = d.getDescription();
        dto.active = d.isActive();
        dto.headEmployeeId = d.getHeadEmployeeId();
        if (d.getParentDepartment() != null) {
            dto.parentDepartmentId = d.getParentDepartment().getId();
            dto.parentDepartmentName = d.getParentDepartment().getName();
        }
        return dto;
    }

    public void setHeadEmployeeName(String headEmployeeName) {
        this.headEmployeeName = headEmployeeName;
    }

    public void setEmployeeCount(long employeeCount) {
        this.employeeCount = employeeCount;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public Long getParentDepartmentId() {
        return parentDepartmentId;
    }

    public String getParentDepartmentName() {
        return parentDepartmentName;
    }

    public Long getHeadEmployeeId() {
        return headEmployeeId;
    }

    public String getHeadEmployeeName() {
        return headEmployeeName;
    }

    public boolean isActive() {
        return active;
    }

    public long getEmployeeCount() {
        return employeeCount;
    }

    public static class CreateRequest {
        @NotBlank(message = "Department name is required")
        private String name;

        @NotBlank(message = "Department code is required")
        private String code;

        private String description;
        private Long parentDepartmentId;
        private Long headEmployeeId;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Long getParentDepartmentId() {
            return parentDepartmentId;
        }

        public void setParentDepartmentId(Long parentDepartmentId) {
            this.parentDepartmentId = parentDepartmentId;
        }

        public Long getHeadEmployeeId() {
            return headEmployeeId;
        }

        public void setHeadEmployeeId(Long headEmployeeId) {
            this.headEmployeeId = headEmployeeId;
        }
    }
}
