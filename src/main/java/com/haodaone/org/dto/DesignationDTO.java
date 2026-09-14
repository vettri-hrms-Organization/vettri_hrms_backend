package com.haodaone.org.dto;

import com.haodaone.org.entity.Designation;
import jakarta.validation.constraints.NotBlank;

public class DesignationDTO {
    private Long id;
    private String title;
    private Integer level;
    private Long departmentId;
    private String departmentName;
    private boolean active;

    public static DesignationDTO from(Designation d) {
        DesignationDTO dto = new DesignationDTO();
        dto.id = d.getId();
        dto.title = d.getTitle();
        dto.level = d.getLevel();
        dto.active = d.isActive();
        if (d.getDepartment() != null) {
            dto.departmentId = d.getDepartment().getId();
            dto.departmentName = d.getDepartment().getName();
        }
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Integer getLevel() {
        return level;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public boolean isActive() {
        return active;
    }

    public static class CreateRequest {
        @NotBlank(message = "Title is required")
        private String title;
        private Integer level;
        private Long departmentId;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Integer getLevel() {
            return level;
        }

        public void setLevel(Integer level) {
            this.level = level;
        }

        public Long getDepartmentId() {
            return departmentId;
        }

        public void setDepartmentId(Long departmentId) {
            this.departmentId = departmentId;
        }
    }
}
