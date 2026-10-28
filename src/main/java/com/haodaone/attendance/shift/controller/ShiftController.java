package com.haodaone.attendance.shift.controller;

import com.haodaone.attendance.shift.dto.ShiftAssignmentDTO;
import com.haodaone.attendance.shift.dto.ShiftAssignmentRequest;
import com.haodaone.attendance.shift.dto.ShiftDTO;
import com.haodaone.attendance.shift.dto.ShiftRequest;
import com.haodaone.attendance.shift.dto.ShiftSettingsDTO;
import com.haodaone.attendance.shift.service.ShiftService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Shift master, department/employee shift assignments and shift/OT/late settings. */
@RestController
@RequestMapping("/api/attendance/shifts")
public class ShiftController {

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW') or hasAuthority('ATTENDANCE_MANAGE')")
    public List<ShiftDTO> list() {
        return shiftService.listShifts();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasOrganizationScope('ATTENDANCE_MANAGE')")
    public ShiftDTO create(@Valid @RequestBody ShiftRequest request) {
        return shiftService.createShift(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasOrganizationScope('ATTENDANCE_MANAGE')")
    public ShiftDTO update(@PathVariable Long id, @Valid @RequestBody ShiftRequest request) {
        return shiftService.updateShift(id, request);
    }

    @PostMapping("/{id}/default")
    @PreAuthorize("@authorizationService.hasOrganizationScope('ATTENDANCE_MANAGE')")
    public ShiftDTO makeDefault(@PathVariable Long id) {
        return shiftService.makeDefault(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasOrganizationScope('ATTENDANCE_MANAGE')")
    public void delete(@PathVariable Long id) {
        shiftService.deleteShift(id);
    }

    @GetMapping("/assignments")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public List<ShiftAssignmentDTO> assignments() {
        return shiftService.listAssignments();
    }

    @PostMapping("/assignments")
    @PreAuthorize("@authorizationService.hasOrganizationScope('ATTENDANCE_MANAGE')")
    public ShiftAssignmentDTO saveAssignment(@Valid @RequestBody ShiftAssignmentRequest request) {
        return shiftService.saveAssignment(request);
    }

    @DeleteMapping("/assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasOrganizationScope('ATTENDANCE_MANAGE')")
    public void deleteAssignment(@PathVariable Long id) {
        shiftService.deleteAssignment(id);
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW') or hasAuthority('ATTENDANCE_MANAGE') or hasAuthority('OVERTIME_APPROVE')")
    public ShiftSettingsDTO settings() {
        return shiftService.getSettings();
    }

    @PutMapping("/settings")
    @PreAuthorize("@authorizationService.hasOrganizationScope('ATTENDANCE_MANAGE')")
    public ShiftSettingsDTO updateSettings(@RequestBody ShiftSettingsDTO request) {
        return shiftService.updateSettings(request);
    }
}
