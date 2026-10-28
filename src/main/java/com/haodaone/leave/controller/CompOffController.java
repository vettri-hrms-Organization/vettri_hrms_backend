package com.haodaone.leave.controller;

import com.haodaone.leave.dto.LeaveDecisionRequest;
import com.haodaone.leave.dto.LeavePolicyDTOs.CompOffApplyRequest;
import com.haodaone.leave.dto.LeavePolicyDTOs.CompOffDTO;
import com.haodaone.leave.service.CompOffService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Comp-off credit requests (V45). Approval mirrors leave: LEAVE_APPROVE over the employee + self-approval guard (in CompOffService). */
@RestController
@RequestMapping("/api/comp-off")
public class CompOffController {

    private final CompOffService compOffService;

    public CompOffController(CompOffService compOffService) {
        this.compOffService = compOffService;
    }

    @PostMapping
    @PreAuthorize("@employeeSecurity.isSelf(#request.employeeId) or @authorizationService.isAllowed('LEAVE_APPLY', 'EMPLOYEE', #request.employeeId)")
    public ResponseEntity<CompOffDTO> apply(@RequestBody CompOffApplyRequest request) {
        return ResponseEntity.status(201).body(compOffService.apply(request));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("@authorizationService.isAllowed('LEAVE_VIEW', 'EMPLOYEE', #employeeId) or hasAuthority('LEAVE_APPLY') and @authorizationService.isAllowed('LEAVE_APPLY', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    public List<CompOffDTO> byEmployee(@PathVariable Long employeeId) {
        return compOffService.listForEmployee(employeeId);
    }

    /** Requests the caller can decide (team for managers, everyone with organization scope). */
    @GetMapping("/approvals")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public List<CompOffDTO> approvals(@RequestParam(required = false) String status) {
        return compOffService.listForApprover(status);
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public CompOffDTO approve(@PathVariable Long id, @RequestBody(required = false) LeaveDecisionRequest request) {
        return compOffService.decide(id, true, request != null ? request.getNote() : null);
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public CompOffDTO reject(@PathVariable Long id, @RequestBody(required = false) LeaveDecisionRequest request) {
        return compOffService.decide(id, false, request != null ? request.getNote() : null);
    }

    /** Owner or an approver over the employee (checked in the service). */
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public CompOffDTO cancel(@PathVariable Long id) {
        return compOffService.cancel(id);
    }
}
