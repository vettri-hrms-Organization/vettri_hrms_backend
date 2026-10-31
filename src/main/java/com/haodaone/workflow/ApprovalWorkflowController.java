package com.haodaone.workflow;

import com.haodaone.security.CustomUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Multi-level approval chains per module (currently LEAVE). Changing one needs the module's manage permission org-wide. */
@RestController
@RequestMapping("/api/approval-workflows")
@PreAuthorize("hasAuthority('LEAVE_MANAGE')")
public class ApprovalWorkflowController {

    private final ApprovalWorkflowService service;

    public ApprovalWorkflowController(ApprovalWorkflowService service) {
        this.service = service;
    }

    @GetMapping("/{module}")
    public ApprovalWorkflowService.WorkflowView get(@PathVariable String module) {
        return service.get(module);
    }

    @PutMapping("/{module}")
    public ApprovalWorkflowService.WorkflowView save(@PathVariable String module, @RequestBody ApprovalWorkflowService.WorkflowRequest request,
                                                     @AuthenticationPrincipal CustomUserPrincipal principal) {
        String actor = principal == null || principal.getUser() == null ? "Admin" : principal.getUser().getFullName();
        return service.save(module, request, actor);
    }
}
