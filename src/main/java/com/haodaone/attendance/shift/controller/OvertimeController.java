package com.haodaone.attendance.shift.controller;

import com.haodaone.attendance.shift.dto.OvertimeClaimRequest;
import com.haodaone.attendance.shift.dto.OvertimeEntryDTO;
import com.haodaone.attendance.shift.dto.OvertimeReviewRequest;
import com.haodaone.attendance.shift.service.OvertimeService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance/overtime")
public class OvertimeController {

    private final OvertimeService overtimeService;

    public OvertimeController(OvertimeService overtimeService) {
        this.overtimeService = overtimeService;
    }

    @GetMapping("/mine")
    @PreAuthorize("@authorizationService.canAccessOwnAttendance()")
    public List<OvertimeEntryDTO> mine() {
        return overtimeService.mine();
    }

    @PostMapping("/claim")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.canAccessOwnAttendance()")
    public OvertimeEntryDTO claim(@Valid @RequestBody OvertimeClaimRequest request) {
        return overtimeService.claim(request.date(), request.note());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('OVERTIME_APPROVE')")
    public List<OvertimeEntryDTO> list(@RequestParam(required = false) String status,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return overtimeService.list(status, from, to);
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("@overtimeService.canReview(#id)")
    public OvertimeEntryDTO review(@PathVariable Long id, @Valid @RequestBody OvertimeReviewRequest request) {
        return overtimeService.review(id, request.approved(), request.approvedMinutes(), request.note());
    }

    /** Re-runs system overtime generation for one day (the nightly job does this automatically). */
    @PostMapping("/generate")
    @PreAuthorize("@authorizationService.hasOrganizationScope('ATTENDANCE_MANAGE')")
    public Map<String, Integer> generate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required.");
        return Map.of("created", overtimeService.generateForDate(companyId, date));
    }
}
