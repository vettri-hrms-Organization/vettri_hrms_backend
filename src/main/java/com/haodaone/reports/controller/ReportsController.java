package com.haodaone.reports.controller;

import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.recruitment.entity.Candidate;
import com.haodaone.recruitment.repository.CandidateRepository;
import com.haodaone.recruitment.repository.JobOpeningRepository;
import com.haodaone.reports.dto.AttendanceReportDTO;
import com.haodaone.reports.dto.EmployeeReportDTO;
import com.haodaone.reports.dto.LeaveReportDTO;
import com.haodaone.reports.dto.RecruitmentReportDTO;
import com.haodaone.reports.service.ReportsService;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real aggregate queries against the same repositories every other module
 * writes to - deliberately not a separate reporting datastore or
 * materialized view at this scale. Every number here reflects live data;
 * none of it is decorative. PDF/Excel export and scheduled report delivery
 * are left to the frontend (CSV export, which any of these JSON shapes
 * trivially convert to) and a future notification/scheduling module
 * respectively - see the README for the reasoning.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private static final List<String> EMPLOYEE_STATUSES = List.of("Active", "On Leave", "Notice Period", "Resigned", "Terminated");
    private static final List<String> EMPLOYMENT_TYPES = List.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERN");
    private static final List<String> CANDIDATE_STAGES = List.of(
            "APPLIED", "SHORTLISTED", "HOLD", "ROUND1", "ROUND2", "ROUND3", "OFFERED", "OFFER_LETTER_SENT", "HIRED", "REJECTED");

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final JobOpeningRepository jobOpeningRepository;
    private final CandidateRepository candidateRepository;
        private final ReportsService reportsService;

    public ReportsController(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository,
                              AttendanceRecordRepository attendanceRecordRepository,
                              LeaveRequestRepository leaveRequestRepository,
                              JobOpeningRepository jobOpeningRepository, CandidateRepository candidateRepository, ReportsService reportsService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.jobOpeningRepository = jobOpeningRepository;
        this.candidateRepository = candidateRepository;
        this.reportsService = reportsService;
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    public EmployeeReportDTO employeeReport() {
        Long companyId = requiredTenant();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        EMPLOYEE_STATUSES.forEach(s -> byStatus.put(s, countStatus(companyId, s)));

        Map<String, Long> byEmploymentType = new LinkedHashMap<>();
        EMPLOYMENT_TYPES.forEach(t -> byEmploymentType.put(t, employeeRepository.countByCompany_IdAndEmploymentTypeAndDeletedFalse(companyId, t)));

        List<EmployeeReportDTO.DepartmentCount> byDepartment = departmentRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(d -> new EmployeeReportDTO.DepartmentCount(d.getId(), d.getName(), employeeRepository.countByDepartmentIdAndDeletedFalse(d.getId())))
                .filter(dc -> dc.getCount() > 0)
                .toList();

        LocalDate now = LocalDate.now();
        return new EmployeeReportDTO(
                employeeRepository.countByCompany_IdAndDeletedFalse(companyId),
                byStatus,
                byEmploymentType,
                byDepartment,
                employeeRepository.countByCompany_IdAndDateOfJoiningGreaterThanEqualAndDeletedFalse(companyId, now.minusDays(30)),
                employeeRepository.countByCompany_IdAndDateOfJoiningGreaterThanEqualAndDeletedFalse(companyId, now.minusDays(90)),
                employeeRepository.countSeparationsSinceForCompany(companyId, LocalDateTime.now().minusDays(90))
        );
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    public AttendanceReportDTO attendanceReport(@RequestParam(required = false) String startDate,
                                                 @RequestParam(required = false) String endDate) {
        Long companyId = requiredTenant();
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : end.minusDays(6);
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.plusDays(1).atStartOfDay();

        // Bounded to a reasonable report window (callers pass sensible ranges;
        // the daily breakdown loop below is O(days), fine for weekly/monthly
        // reports but not intended for multi-year ranges).
        List<AttendanceReportDTO.DailyCount> daily = start.datesUntil(end.plusDays(1))
                .map(date -> new AttendanceReportDTO.DailyCount(date,
                        attendanceRecordRepository.countDistinctEmployeesPunchedBetweenForCompany(companyId, date.atStartOfDay(), date.plusDays(1).atStartOfDay())))
                .toList();

        List<AttendanceReportDTO.DepartmentPunchCount> byDepartment = attendanceRecordRepository
                .countByDepartmentBetweenForCompany(companyId, startDateTime, endDateTime).stream()
                .map(row -> new AttendanceReportDTO.DepartmentPunchCount((String) row[0], (Long) row[1]))
                .toList();

        return new AttendanceReportDTO(
                start, end,
                attendanceRecordRepository.countByCompany_IdAndPunchTimeBetween(companyId, startDateTime, endDateTime),
                attendanceRecordRepository.countDistinctEmployeesPunchedBetweenForCompany(companyId, startDateTime, endDateTime),
                countStatus(companyId, EmploymentStatus.ACTIVE),
                daily,
                byDepartment
        );
    }

    @GetMapping("/leave")
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    public LeaveReportDTO leaveReport(@RequestParam(required = false) Integer year) {
        int targetYear = year != null ? year : LocalDate.now().getYear();
        LocalDate yearStart = LocalDate.of(targetYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(targetYear, 12, 31);

        List<LeaveReportDTO.LeaveTypeUsage> byLeaveType = leaveRequestRepository.sumApprovedDaysByLeaveType(targetYear).stream()
                .map(row -> new LeaveReportDTO.LeaveTypeUsage((String) row[0], ((Number) row[1]).doubleValue()))
                .toList();

        List<LeaveReportDTO.DepartmentUsage> byDepartment = leaveRequestRepository.sumApprovedDaysByDepartment(targetYear).stream()
                .map(row -> new LeaveReportDTO.DepartmentUsage((String) row[0], ((Number) row[1]).doubleValue()))
                .toList();

        long approved = leaveRequestRepository.countByStatusAndStartDateBetween("APPROVED", yearStart, yearEnd);
        long rejected = leaveRequestRepository.countByStatusAndStartDateBetween("REJECTED", yearStart, yearEnd);
        long pending = leaveRequestRepository.countByStatusAndStartDateBetween("PENDING", yearStart, yearEnd);
        long cancelled = leaveRequestRepository.countByStatusAndStartDateBetween("CANCELLED", yearStart, yearEnd);

        return new LeaveReportDTO(targetYear, approved + rejected + pending + cancelled, approved, rejected, pending, cancelled,
                byLeaveType, byDepartment);
    }

    @GetMapping("/recruitment")
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    public RecruitmentReportDTO recruitmentReport() {
        Long companyId = requiredTenant();
        Map<String, Long> byStage = new LinkedHashMap<>();
        CANDIDATE_STAGES.forEach(s -> byStage.put(s, candidateRepository.countByJobOpening_Company_IdAndStageAndDeletedFalse(companyId, s)));

        int currentYear = LocalDate.now().getYear();
        List<Candidate> hiredThisYear = candidateRepository.findHiredInYearForCompany(companyId, currentYear);

        Double avgDaysToHire = hiredThisYear.isEmpty() ? null : hiredThisYear.stream()
                .mapToLong(c -> ChronoUnit.DAYS.between(c.getAppliedDate(), c.getUpdatedAt().toLocalDate()))
                .average()
                .orElse(0);

        return new RecruitmentReportDTO(
                jobOpeningRepository.countByCompany_IdAndStatusAndDeletedFalse(companyId, "OPEN"),
                candidateRepository.countByJobOpening_Company_IdAndDeletedFalse(companyId),
                byStage,
                hiredThisYear.size(),
                avgDaysToHire != null ? Math.round(avgDaysToHire * 10) / 10.0 : null
        );
    }

        @GetMapping("/departments/comparison")
        @PreAuthorize("hasAuthority('REPORTS_VIEW')")
        public List<Map<String,Object>> departmentComparison(@RequestParam(required = false) String startDate,
                                                                                                                   @RequestParam(required = false) String endDate) {
                Long companyId = requiredTenant();
                LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
                LocalDate start = startDate != null ? LocalDate.parse(startDate) : end.minusDays(29);
                return reportsService.departmentComparison(companyId, start, end);
        }

        private Long requiredTenant() {
                Long tenant = TenantContext.getCurrentTenant();
                if (tenant == null) {
                        throw new IllegalStateException("Company context is required");
                }
                return tenant;
        }

        private long countStatus(Long companyId, String status) {
                return employeeRepository.countByCompany_IdAndStatusInAndDeletedFalse(companyId, EmploymentStatus.aliases(status));
        }
}
