package com.haodaone.performance.dto;

import com.haodaone.performance.entity.PerformanceReview;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class PerformanceReviewDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private Long reviewerId;
    private String reviewerName;
    private String reviewPeriod;
    private Integer rating;
    private String strengths;
    private String areasForImprovement;
    private String status;
    private LocalDateTime submittedAt;

    public static PerformanceReviewDTO from(PerformanceReview r) {
        PerformanceReviewDTO dto = new PerformanceReviewDTO();
        dto.id = r.getId();
        dto.employeeId = r.getEmployee().getId();
        dto.employeeName = r.getEmployee().getFullName();
        dto.reviewPeriod = r.getReviewPeriod();
        dto.rating = r.getRating();
        dto.strengths = r.getStrengths();
        dto.areasForImprovement = r.getAreasForImprovement();
        dto.status = r.getStatus();
        dto.submittedAt = r.getSubmittedAt();
        if (r.getReviewer() != null) {
            dto.reviewerId = r.getReviewer().getId();
            dto.reviewerName = r.getReviewer().getFullName();
        }
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

    public Long getReviewerId() {
        return reviewerId;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public String getReviewPeriod() {
        return reviewPeriod;
    }

    public Integer getRating() {
        return rating;
    }

    public String getStrengths() {
        return strengths;
    }

    public String getAreasForImprovement() {
        return areasForImprovement;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public static class CreateRequest {
        @NotNull(message = "Employee is required")
        private Long employeeId;
        private Long reviewerId;

        @NotBlank(message = "Review period is required")
        private String reviewPeriod;

        private Integer rating;
        private String strengths;
        private String areasForImprovement;

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
        }

        public Long getReviewerId() {
            return reviewerId;
        }

        public void setReviewerId(Long reviewerId) {
            this.reviewerId = reviewerId;
        }

        public String getReviewPeriod() {
            return reviewPeriod;
        }

        public void setReviewPeriod(String reviewPeriod) {
            this.reviewPeriod = reviewPeriod;
        }

        public Integer getRating() {
            return rating;
        }

        public void setRating(Integer rating) {
            this.rating = rating;
        }

        public String getStrengths() {
            return strengths;
        }

        public void setStrengths(String strengths) {
            this.strengths = strengths;
        }

        public String getAreasForImprovement() {
            return areasForImprovement;
        }

        public void setAreasForImprovement(String areasForImprovement) {
            this.areasForImprovement = areasForImprovement;
        }
    }
}
