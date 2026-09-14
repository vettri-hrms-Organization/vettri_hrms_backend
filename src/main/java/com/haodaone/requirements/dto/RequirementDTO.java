package com.haodaone.requirements.dto;

import com.haodaone.requirements.entity.Requirement;
import com.haodaone.requirements.entity.RequirementStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record RequirementDTO(Long id, String title, String description, RequirementStatus status, Long assignedToUserId,
                             String assignedToName, LocalDate dueDate, String closeReason, Long closedByUserId, LocalDateTime closedAt) {
    public static RequirementDTO from(Requirement r) {
        return new RequirementDTO(r.getId(), r.getTitle(), r.getDescription(), r.getStatus(),
                r.getAssignedTo() == null ? null : r.getAssignedTo().getId(),
                r.getAssignedTo() == null ? null : r.getAssignedTo().getFullName(), r.getDueDate(), r.getCloseReason(),
                r.getClosedBy() == null ? null : r.getClosedBy().getId(), r.getClosedAt());
    }
    public record WriteRequest(@NotBlank String title, String description, Long assignedToUserId, LocalDate dueDate) { }
    public record StatusRequest(RequirementStatus status, String closeReason) { }
}