package com.haodaone.attendance.controller;

import com.haodaone.attendance.dto.AttendanceContextDTO;
import com.haodaone.attendance.dto.AttendanceBiometricDeviceDTO;
import com.haodaone.attendance.dto.AttendancePolicyDTO;
import com.haodaone.attendance.dto.EmployeeAttendanceConfigurationDTO;
import com.haodaone.attendance.dto.EmployeeAttendanceConfigurationRequest;
import com.haodaone.attendance.service.AttendancePolicyService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendancePolicyController {

    private final AttendancePolicyService attendancePolicyService;
    private final com.haodaone.security.EmployeeSecurity employeeSecurity;

    public AttendancePolicyController(AttendancePolicyService attendancePolicyService,
                                      com.haodaone.security.EmployeeSecurity employeeSecurity) {
        this.attendancePolicyService = attendancePolicyService;
        this.employeeSecurity = employeeSecurity;
    }

    @GetMapping("/policy")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW') or @authorizationService.canAccessOwnAttendance()")
    public AttendancePolicyDTO policy() {
        return attendancePolicyService.getPolicy();
    }

    @PutMapping("/policy")
    @PreAuthorize("@authorizationService.hasOrganizationScope('ATTENDANCE_MANAGE')")
    public AttendancePolicyDTO updatePolicy(@Valid @RequestBody AttendancePolicyDTO request) {
        return attendancePolicyService.updatePolicy(request);
    }

    @GetMapping("/policy/context")
    @PreAuthorize("@authorizationService.canAccessOwnAttendance()")
    public AttendanceContextDTO currentContext() {
        return attendancePolicyService.getCurrentContext();
    }

    @GetMapping("/employee/{employeeId}/configuration")
    @PreAuthorize("@authorizationService.isAllowed('ATTENDANCE_VIEW', 'EMPLOYEE', #employeeId) or @authorizationService.isAllowed('ATTENDANCE_MANAGE', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    public EmployeeAttendanceConfigurationDTO employeeConfiguration(@PathVariable Long employeeId) {
        EmployeeAttendanceConfigurationDTO configuration = attendancePolicyService.getEmployeeConfiguration(employeeId);
        if (employeeSecurity.isSelf(employeeId)) {
            return new EmployeeAttendanceConfigurationDTO(
                    configuration.attendanceMethodOverride(),
                    configuration.effectiveAttendanceMethod(),
                    null,
                    null,
                    null,
                    configuration.biometricEnrolled());
        }
        return configuration;
    }

    @PutMapping("/employee/{employeeId}/configuration")
    @PreAuthorize("@authorizationService.isAllowed('ATTENDANCE_MANAGE', 'EMPLOYEE', #employeeId)")
    public EmployeeAttendanceConfigurationDTO updateEmployeeConfiguration(
            @PathVariable Long employeeId,
            @Valid @RequestBody EmployeeAttendanceConfigurationRequest request) {
        return attendancePolicyService.updateEmployeeConfiguration(employeeId, request);
    }

    @GetMapping("/employee/{employeeId}/biometric-devices")
    @PreAuthorize("@authorizationService.isAllowed('ATTENDANCE_MANAGE', 'EMPLOYEE', #employeeId)")
    public List<AttendanceBiometricDeviceDTO> biometricDevices(@PathVariable Long employeeId) {
        return attendancePolicyService.biometricDevices(employeeId);
    }
}
