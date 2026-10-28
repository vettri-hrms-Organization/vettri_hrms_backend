package com.haodaone.attendance.controller;

import com.haodaone.attendance.service.AttendanceMusterService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceMusterController {

    private final AttendanceMusterService attendanceMusterService;

    public AttendanceMusterController(AttendanceMusterService attendanceMusterService) {
        this.attendanceMusterService = attendanceMusterService;
    }

    /** Monthly muster (.xlsx), scoped to the employees the caller may see under ATTENDANCE_VIEW. */
    @GetMapping("/muster/export")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public ResponseEntity<byte[]> exportMonthlyMuster(@RequestParam int year, @RequestParam int month) {
        byte[] workbook = attendanceMusterService.exportMonthlyMuster(year, month);
        String filename = String.format("attendance-muster-%04d-%02d.xlsx", year, month);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbook);
    }
}
