package com.haodaone.leave.controller;

import com.haodaone.leave.dto.LeaveDecisionRequest;
import com.haodaone.leave.dto.LeavePolicyDTOs.EncashmentApplyRequest;
import com.haodaone.leave.dto.LeavePolicyDTOs.EncashmentDTO;
import com.haodaone.leave.service.LeaveEncashmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Leave encashment requests (V45): employee/self requests, HR with organization-scope LEAVE_MANAGE decides. */
@RestController
@RequestMapping("/api/leave-encashments")
public class LeaveEncashmentController {

    private final LeaveEncashmentService leaveEncashmentService;

    public LeaveEncashmentController(LeaveEncashmentService leaveEncashmentService) {
        this.leaveEncashmentService = leaveEncashmentService;
    }

    @PostMapping
    @PreAuthorize("@employeeSecurity.isSelf(#request.employeeId) or @authorizationService.isAllowed('LEAVE_APPLY', 'EMPLOYEE', #request.employeeId)")
    public ResponseEntity<EncashmentDTO> request(@RequestBody EncashmentApplyRequest request) {
        return ResponseEntity.status(201).body(leaveEncashmentService.request(request));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("@authorizationService.isAllowed('LEAVE_VIEW', 'EMPLOYEE', #employeeId) or hasAuthority('LEAVE_APPLY') and @authorizationService.isAllowed('LEAVE_APPLY', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    public List<EncashmentDTO> byEmployee(@PathVariable Long employeeId) {
        return leaveEncashmentService.listForEmployee(employeeId);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasOrganizationScope('LEAVE_MANAGE')")
    public List<EncashmentDTO> list(@RequestParam(required = false) String status) {
        return leaveEncashmentService.listAll(status);
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("@authorizationService.hasOrganizationScope('LEAVE_MANAGE')")
    public EncashmentDTO approve(@PathVariable Long id, @RequestBody(required = false) LeaveDecisionRequest request) {
        return leaveEncashmentService.decide(id, true, request != null ? request.getNote() : null);
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("@authorizationService.hasOrganizationScope('LEAVE_MANAGE')")
    public EncashmentDTO reject(@PathVariable Long id, @RequestBody(required = false) LeaveDecisionRequest request) {
        return leaveEncashmentService.decide(id, false, request != null ? request.getNote() : null);
    }

    /** Requester only (checked in the service). */
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public EncashmentDTO cancel(@PathVariable Long id) {
        return leaveEncashmentService.cancel(id);
    }
}
