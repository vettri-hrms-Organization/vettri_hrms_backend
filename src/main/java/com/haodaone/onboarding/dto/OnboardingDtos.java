package com.haodaone.onboarding.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response shapes for the onboarding / offboarding checklist API. */
public final class OnboardingDtos {

    private OnboardingDtos() {
    }

    public record TemplateTaskDTO(Long id, String title, String description, String ownerType, Long ownerUserId,
                                  String ownerUserName, int dueOffsetDays, boolean requiresDocument,
                                  String documentType, String linkKey, int sortOrder) {}

    public record TemplateDTO(Long id, String name, String templateType, String description, boolean defaultTemplate,
                              boolean autoStart, boolean active, List<TemplateTaskDTO> tasks) {}

    public record TemplateTaskRequest(String title, String description, String ownerType, Long ownerUserId,
                                      Integer dueOffsetDays, Boolean requiresDocument, String documentType, String linkKey) {}

    public record TemplateRequest(String name, String templateType, String description, Boolean defaultTemplate,
                                  Boolean autoStart, Boolean active, List<TemplateTaskRequest> tasks) {}

    public record TaskDTO(Long id, Long checklistId, String title, String description, String ownerType,
                          Long ownerUserId, String ownerName, Long ownerEmployeeId, LocalDate dueDate,
                          String status, boolean overdue, LocalDateTime completedAt, String completedBy,
                          String remarks, boolean requiresDocument, String documentType, Long documentId,
                          String linkKey, boolean canComplete,
                          Long employeeId, String employeeName, String checklistType) {}

    public record ChecklistDTO(Long id, Long employeeId, String employeeName, String employeeCode,
                               String checklistType, String name, String status, LocalDate anchorDate,
                               LocalDateTime startedAt, LocalDateTime completedAt, int totalTasks, int doneTasks,
                               int overdueTasks, int progressPercent, List<TaskDTO> tasks) {}

    public record StartRequest(Long employeeId, Long templateId, String checklistType, LocalDate anchorDate) {}

    public record CompleteRequest(String remarks, Boolean skip) {}

    public record DashboardDTO(int inProgress, int overdueTasks, int dueThisWeek, List<ChecklistDTO> checklists,
                               List<TaskDTO> overdue) {}
}
