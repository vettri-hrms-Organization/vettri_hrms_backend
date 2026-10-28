package com.haodaone.leave.controller;

import com.haodaone.leave.dto.HolidayDTO;
import com.haodaone.leave.service.HolidayService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayService holidayService;
    private final com.haodaone.leave.service.OptionalHolidayService optionalHolidayService;

    public HolidayController(HolidayService holidayService,
                             com.haodaone.leave.service.OptionalHolidayService optionalHolidayService) {
        this.holidayService = holidayService;
        this.optionalHolidayService = optionalHolidayService;
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasOrganizationScope('LEAVE_MANAGE')")
    public HolidayDTO update(@PathVariable Long id, @Valid @RequestBody HolidayDTO.CreateRequest request) {
        return holidayService.update(id, request);
    }

    /** Holidays that apply to the signed-in employee (location-aware) with optional-holiday picks. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public com.haodaone.leave.dto.EmployeeHolidaysDTO mine(@RequestParam(required = false) Integer year) {
        return optionalHolidayService.mine(year);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("@authorizationService.isAllowed('LEAVE_VIEW', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    public com.haodaone.leave.dto.EmployeeHolidaysDTO forEmployee(@PathVariable Long employeeId,
                                                                 @RequestParam(required = false) Integer year) {
        return optionalHolidayService.forEmployee(employeeId, year);
    }

    @PostMapping("/{id}/pick")
    @PreAuthorize("isAuthenticated()")
    public com.haodaone.leave.dto.EmployeeHolidaysDTO pick(@PathVariable Long id) {
        return optionalHolidayService.pick(id);
    }

    @DeleteMapping("/{id}/pick")
    @PreAuthorize("isAuthenticated()")
    public com.haodaone.leave.dto.EmployeeHolidaysDTO unpick(@PathVariable Long id) {
        return optionalHolidayService.unpick(id);
    }

    @GetMapping("/settings")
    @PreAuthorize("isAuthenticated()")
    public com.haodaone.leave.dto.EmployeeHolidaysDTO.SettingsDTO settings() {
        return optionalHolidayService.getSettings();
    }

    @PutMapping("/settings")
    @PreAuthorize("@authorizationService.hasOrganizationScope('LEAVE_MANAGE')")
    public com.haodaone.leave.dto.EmployeeHolidaysDTO.SettingsDTO updateSettings(
            @RequestBody com.haodaone.leave.dto.EmployeeHolidaysDTO.SettingsDTO request) {
        return optionalHolidayService.updateSettings(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('EMPLOYEE') or hasAuthority('SELF_LEAVE_VIEW') or hasAuthority('LEAVE_APPLY') or hasAuthority('LEAVE_VIEW') or hasAuthority('LEAVE_MANAGE')")
    public List<HolidayDTO> listAll() {
        return holidayService.listAll();
    }

    @PostMapping
    @PreAuthorize("@authorizationService.hasOrganizationScope('LEAVE_MANAGE')")
    public ResponseEntity<HolidayDTO> create(@Valid @RequestBody HolidayDTO.CreateRequest request) {
        return ResponseEntity.status(201).body(holidayService.create(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.hasOrganizationScope('LEAVE_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        holidayService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
