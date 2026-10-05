package com.haodaone.attendance.controller;

import com.haodaone.attendance.dto.AttendanceRegularizationDTO;
import com.haodaone.attendance.dto.AttendanceRegularizationRequest;
import com.haodaone.attendance.dto.AttendanceRegularizationReviewRequest;
import com.haodaone.attendance.service.AttendanceRegularizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/attendance/regularizations")
public class AttendanceRegularizationController {

    private final AttendanceRegularizationService regularizationService;

    public AttendanceRegularizationController(AttendanceRegularizationService regularizationService) {
        this.regularizationService = regularizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.canAccessOwnAttendance()")
    public AttendanceRegularizationDTO submit(@Valid @RequestBody AttendanceRegularizationRequest request) {
        return regularizationService.submit(request);
    }

    @GetMapping("/mine")
    @PreAuthorize("@authorizationService.canAccessOwnAttendance()")
    public List<AttendanceRegularizationDTO> mine() {
        return regularizationService.mine();
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public List<AttendanceRegularizationDTO> pending() {
        return regularizationService.pending();
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("@attendanceRegularizationService.canReview(#id)")
    public AttendanceRegularizationDTO review(
            @PathVariable Long id,
            @Valid @RequestBody AttendanceRegularizationReviewRequest request) {
        return regularizationService.review(id, request.approved(), request.note());
    }
}
