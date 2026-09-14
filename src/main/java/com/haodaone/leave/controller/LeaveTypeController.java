package com.haodaone.leave.controller;

import com.haodaone.leave.dto.LeaveTypeDTO;
import com.haodaone.leave.service.LeaveTypeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave-types")
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    public LeaveTypeController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LEAVE_APPLY') or hasAuthority('LEAVE_VIEW') or hasAuthority('LEAVE_MANAGE')")
    public List<LeaveTypeDTO> listAll() {
        return leaveTypeService.listAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LEAVE_MANAGE')")
    public ResponseEntity<LeaveTypeDTO> create(@Valid @RequestBody LeaveTypeDTO.CreateRequest request) {
        return ResponseEntity.status(201).body(leaveTypeService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAVE_MANAGE')")
    public LeaveTypeDTO update(@PathVariable Long id, @Valid @RequestBody LeaveTypeDTO.CreateRequest request) {
        return leaveTypeService.update(id, request);
    }
}
