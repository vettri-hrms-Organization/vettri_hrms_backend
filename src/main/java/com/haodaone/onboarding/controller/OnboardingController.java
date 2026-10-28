package com.haodaone.onboarding.controller;

import com.haodaone.onboarding.dto.OnboardingDtos;
import com.haodaone.onboarding.service.OnboardingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Onboarding / offboarding checklists. HR endpoints need ONBOARDING_MANAGE
 * with organization scope; task completion is checked per task in the
 * service (HR, or the task's owner: the new hire, their manager, IT, or a
 * named user).
 */
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private static final String MANAGE =
            "hasAuthority('ONBOARDING_MANAGE') and @authorizationService.hasOrganizationScope('ONBOARDING_MANAGE')";

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    // ---------------------------------------------------------------- templates

    @GetMapping("/templates")
    @PreAuthorize(MANAGE)
    public List<OnboardingDtos.TemplateDTO> templates() {
        return onboardingService.listTemplates();
    }

    @PostMapping("/templates")
    @PreAuthorize(MANAGE)
    public ResponseEntity<OnboardingDtos.TemplateDTO> createTemplate(@RequestBody OnboardingDtos.TemplateRequest request) {
        return ResponseEntity.status(201).body(onboardingService.createTemplate(request));
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize(MANAGE)
    public OnboardingDtos.TemplateDTO updateTemplate(@PathVariable Long id, @RequestBody OnboardingDtos.TemplateRequest request) {
        return onboardingService.updateTemplate(id, request);
    }

    @DeleteMapping("/templates/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        onboardingService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- checklists

    @GetMapping("/dashboard")
    @PreAuthorize(MANAGE)
    public OnboardingDtos.DashboardDTO dashboard() {
        return onboardingService.dashboard();
    }

    @PostMapping("/checklists")
    @PreAuthorize(MANAGE)
    public ResponseEntity<OnboardingDtos.ChecklistDTO> start(@RequestBody OnboardingDtos.StartRequest request) {
        return ResponseEntity.status(201).body(onboardingService.start(request));
    }

    @PostMapping("/checklists/{id}/cancel")
    @PreAuthorize(MANAGE)
    public OnboardingDtos.ChecklistDTO cancel(@PathVariable Long id) {
        return onboardingService.cancel(id);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("(" + MANAGE + ") or @employeeSecurity.isSelf(#employeeId)"
            + " or @authorizationService.isAllowed('EMPLOYEE_VIEW', 'EMPLOYEE', #employeeId)")
    public List<OnboardingDtos.ChecklistDTO> forEmployee(@PathVariable Long employeeId) {
        return onboardingService.forEmployee(employeeId);
    }

    /** "My onboarding": the signed-in employee's own checklists. */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public List<OnboardingDtos.ChecklistDTO> mine() {
        return onboardingService.mine();
    }

    /** Tasks the signed-in user owns on other employees' checklists (manager / IT / named user). */
    @GetMapping("/my-tasks")
    @PreAuthorize("isAuthenticated()")
    public List<OnboardingDtos.TaskDTO> myTasks() {
        return onboardingService.myTasks();
    }

    // ---------------------------------------------------------------- tasks (ownership checked in the service)

    @PostMapping("/tasks/{taskId}/complete")
    @PreAuthorize("isAuthenticated()")
    public OnboardingDtos.TaskDTO complete(@PathVariable Long taskId,
                                           @RequestBody(required = false) OnboardingDtos.CompleteRequest request) {
        return onboardingService.complete(taskId, request);
    }

    @PostMapping("/tasks/{taskId}/reopen")
    @PreAuthorize("isAuthenticated()")
    public OnboardingDtos.TaskDTO reopen(@PathVariable Long taskId) {
        return onboardingService.reopen(taskId);
    }

    @PostMapping(value = "/tasks/{taskId}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public OnboardingDtos.TaskDTO uploadDocument(@PathVariable Long taskId, @RequestPart("file") MultipartFile file) {
        return onboardingService.uploadDocument(taskId, file);
    }
}
