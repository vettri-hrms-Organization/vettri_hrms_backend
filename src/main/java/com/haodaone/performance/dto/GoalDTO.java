package com.haodaone.performance.dto;

import com.haodaone.performance.entity.Goal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class GoalDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String title;
    private String description;
    private LocalDate targetDate;
    private String status;
    private int progressPercent;

    public static GoalDTO from(Goal g) {
        GoalDTO dto = new GoalDTO();
        dto.id = g.getId();
        dto.employeeId = g.getEmployee().getId();
        dto.employeeName = g.getEmployee().getFullName();
        dto.title = g.getTitle();
        dto.description = g.getDescription();
        dto.targetDate = g.getTargetDate();
        dto.status = g.getStatus();
        dto.progressPercent = g.getProgressPercent();
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

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public String getStatus() {
        return status;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public static class CreateRequest {
        @NotNull(message = "Employee is required")
        private Long employeeId;

        @NotBlank(message = "Title is required")
        private String title;

        private String description;
        private LocalDate targetDate;

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public LocalDate getTargetDate() {
            return targetDate;
        }

        public void setTargetDate(LocalDate targetDate) {
            this.targetDate = targetDate;
        }
    }

    public static class ProgressUpdateRequest {
        @Min(0)
        @Max(100)
        private int progressPercent;

        @NotBlank(message = "Status is required")
        private String status;

        public int getProgressPercent() {
            return progressPercent;
        }

        public void setProgressPercent(int progressPercent) {
            this.progressPercent = progressPercent;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
