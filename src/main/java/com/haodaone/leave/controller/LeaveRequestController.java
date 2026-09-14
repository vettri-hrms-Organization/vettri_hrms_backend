package com.haodaone.leave.controller;

import com.haodaone.leave.dto.ApplyLeaveRequest;
import com.haodaone.leave.dto.LeaveBalanceDTO;
import com.haodaone.leave.dto.LeaveDecisionRequest;
import com.haodaone.leave.dto.LeaveRequestDTO;
import com.haodaone.leave.service.LeaveRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LEAVE_VIEW')")
    public List<LeaveRequestDTO> listAll(@RequestParam(required = false) String status) {
        return leaveRequestService.listAll(status);
    }

    /**
     * Team-scoped equivalent of listAll, for the /leave page's Manager
     * view. listAll above is gated on LEAVE_VIEW, which MANAGER also
     * holds (alongside HR_ADMIN) - meaning a Manager hitting listAll
     * directly gets every employee's leave requests company-wide, not
     * just their team's. The frontend now calls this instead whenever the
     * user has LEAVE_APPROVE without LEAVE_MANAGE (see useAuth().hasPermission
     * usage in LeaveRequests.jsx) - same distinction already used for the
     * Dashboard's My Team widget.
     */
    @GetMapping("/team")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public List<LeaveRequestDTO> listForMyTeam(@RequestParam(required = false) String status) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return leaveRequestService.listForManagerTeam(username, status);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('LEAVE_VIEW') or hasAuthority('LEAVE_APPLY') or @employeeSecurity.isSelf(#employeeId)")
    public List<LeaveRequestDTO> byEmployee(@PathVariable Long employeeId) {
        return leaveRequestService.listByEmployee(employeeId);
    }

    @GetMapping("/employee/{employeeId}/balance")
    @PreAuthorize("hasAuthority('LEAVE_VIEW') or hasAuthority('LEAVE_APPLY') or @employeeSecurity.isSelf(#employeeId)")
    public List<LeaveBalanceDTO> balance(@PathVariable Long employeeId,
                                          @RequestParam(required = false) Integer year) {
        return leaveRequestService.getBalances(employeeId, year != null ? year : LocalDate.now().getYear());
    }

    /**
     * LEAVE_APPLY ("apply on behalf of employees" - see DataSeeder) covers
     * HR/Managers filing for someone else. An employee filing for
     * themselves doesn't need that broader permission at all - the
     * isSelf bypass against the request body's own employeeId covers it,
     * and since the check runs before the method body executes, nothing
     * downstream can be reached with an unauthorized employeeId.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('LEAVE_APPLY') or @employeeSecurity.isSelf(#request.employeeId)")
    public ResponseEntity<LeaveRequestDTO> apply(@Valid @RequestBody ApplyLeaveRequest request) {
        return ResponseEntity.status(201).body(leaveRequestService.apply(request));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public LeaveRequestDTO approve(@PathVariable Long id, @RequestBody(required = false) LeaveDecisionRequest request) {
        return leaveRequestService.decide(id, true, request != null ? request.getNote() : null);
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public LeaveRequestDTO reject(@PathVariable Long id, @RequestBody(required = false) LeaveDecisionRequest request) {
        return leaveRequestService.decide(id, false, request != null ? request.getNote() : null);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('LEAVE_APPLY') or hasAuthority('LEAVE_APPROVE') or @employeeSecurity.ownsLeaveRequest(#id)")
    public LeaveRequestDTO cancel(@PathVariable Long id) {
        return leaveRequestService.cancel(id);
    }
}
