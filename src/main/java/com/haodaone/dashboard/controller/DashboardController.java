package com.haodaone.dashboard.controller;

import com.haodaone.dashboard.dto.DashboardSummaryDTO;
import com.haodaone.dashboard.dto.RecruiterDashboardDTO;
import com.haodaone.dashboard.dto.TeamDashboardDTO;
import com.haodaone.employee.dto.EmployeeSummaryDTO;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.service.LeaveRequestService;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.recruitment.dto.CandidateDTO;
import com.haodaone.recruitment.dto.InterviewDTO;
import com.haodaone.recruitment.dto.JobOpeningDTO;
import com.haodaone.recruitment.repository.CandidateRepository;
import com.haodaone.recruitment.repository.InterviewRepository;
import com.haodaone.recruitment.repository.JobOpeningRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Powers the Dashboard "Command Center". Deliberately just aggregation
 * queries against existing repositories rather than a separate reporting
 * datastore - fine at Phase 1 scale; revisit with materialized views or a
 * read replica if/when this becomes a real bottleneck.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final int BIRTHDAY_LOOKAHEAD_DAYS = 7;

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveRequestService leaveRequestService;
    private final JobOpeningRepository jobOpeningRepository;
    private final CandidateRepository candidateRepository;
    private final InterviewRepository interviewRepository;

    public DashboardController(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository,
                                LeaveRequestService leaveRequestService, JobOpeningRepository jobOpeningRepository,
                                CandidateRepository candidateRepository, InterviewRepository interviewRepository) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.leaveRequestService = leaveRequestService;
        this.jobOpeningRepository = jobOpeningRepository;
        this.candidateRepository = candidateRepository;
        this.interviewRepository = interviewRepository;
    }

    @GetMapping("/summary")
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('EMPLOYEE_VIEW')")
    @Transactional(readOnly = true)
    public DashboardSummaryDTO summary() {
        Long companyId = requiredTenant();
        long total = employeeRepository.countByCompany_IdAndDeletedFalse(companyId);
        long active = countStatus(companyId, EmploymentStatus.ACTIVE);
        long onLeave = countStatus(companyId, EmploymentStatus.ON_LEAVE);
        long noticePeriod = countStatus(companyId, EmploymentStatus.NOTICE_PERIOD);
        long resigned = countStatus(companyId, EmploymentStatus.RESIGNED);
        long terminated = countStatus(companyId, EmploymentStatus.TERMINATED);

        List<DashboardSummaryDTO.DepartmentCount> departmentBreakdown = departmentRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(dept -> new DashboardSummaryDTO.DepartmentCount(
                        dept.getName(), employeeRepository.countByDepartmentIdAndDeletedFalse(dept.getId())))
                .filter(dc -> dc.getCount() > 0)
                .toList();

        List<EmployeeSummaryDTO> recentJoiners = employeeRepository.findTop5ByCompany_IdAndDeletedFalseOrderByDateOfJoiningDesc(companyId).stream()
                .map(EmployeeSummaryDTO::from)
                .toList();

        return new DashboardSummaryDTO(total, active, onLeave, noticePeriod, resigned, terminated,
                departmentBreakdown, recentJoiners, upcomingBirthdays(companyId));
    }

    /**
     * The Manager persona's team-scoped view: direct reports plus only
     * their pending leave requests, not the whole company's.
     *
     * Resolves "my team" via the current login's linked Employee record
     * (Employee.user - see User.java for why that link exists rather than
     * assuming every login corresponds to an employee). A user with no
     * linked Employee record (e.g. an external auditor account, or
     * SUPER_ADMIN if it wasn't seeded with one) gets an empty team back
     * rather than an error - "not a people manager" is a valid answer here,
     * not a failure.
     */
    @GetMapping("/my-team")
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('LEAVE_APPROVE')")
    @Transactional(readOnly = true)
    public TeamDashboardDTO myTeam() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Employee me = employeeRepository.findByUser_UsernameAndDeletedFalse(username).orElse(null);
        if (me == null) {
            return new TeamDashboardDTO(List.of(), List.of());
        }

        List<Employee> directReports = employeeRepository.findAllByReportingManagerIdAndDeletedFalse(me.getId());
        List<EmployeeSummaryDTO> teamMembers = directReports.stream().map(EmployeeSummaryDTO::from).toList();

        return new TeamDashboardDTO(teamMembers, leaveRequestService.listForManagerTeam(username, "PENDING"));
    }

    /**
     * Active employees whose birthday (month+day, year ignored) falls within
     * the next {@link #BIRTHDAY_LOOKAHEAD_DAYS} days, today included, sorted
     * by how soon it comes up. Computed in Java rather than a DB date-diff
     * query so this doesn't depend on the target database's date functions,
     * and because it correctly handles the year-wraparound case (e.g. today
     * is Dec 29th, someone's birthday is Jan 2nd) without special-casing it
     * in SQL.
     */
    private List<DashboardSummaryDTO.UpcomingBirthday> upcomingBirthdays(Long companyId) {
        LocalDate today = LocalDate.now();

        return employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId).stream()
                .filter(e -> "Active".equals(e.getStatus()) && e.getDateOfBirth() != null)
                .map(e -> new Object[]{e, daysUntilNextBirthday(e.getDateOfBirth(), today)})
                .filter(pair -> (int) pair[1] < BIRTHDAY_LOOKAHEAD_DAYS)
                .sorted(Comparator.comparingInt(pair -> (int) pair[1]))
                .map(pair -> {
                    Employee e = (Employee) pair[0];
                    return new DashboardSummaryDTO.UpcomingBirthday(
                            e.getId(), e.getFirstName() + " " + e.getLastName(), e.getDateOfBirth(), e.getProfilePhotoUrl());
                })
                .toList();
    }

    private Long requiredTenant() {
        Long tenant = com.haodaone.tenant.TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new IllegalStateException("Company context is required");
        }
        return tenant;
    }

    private long countStatus(Long companyId, String status) {
        return employeeRepository.countByCompany_IdAndStatusInAndDeletedFalse(companyId, EmploymentStatus.aliases(status));
    }

    private int daysUntilNextBirthday(LocalDate dateOfBirth, LocalDate today) {
        MonthDay birthdayMonthDay = MonthDay.from(dateOfBirth);
        LocalDate nextOccurrence = birthdayMonthDay.atYear(today.getYear());
        if (nextOccurrence.isBefore(today)) {
            nextOccurrence = birthdayMonthDay.atYear(today.getYear() + 1);
        }
        return (int) ChronoUnit.DAYS.between(today, nextOccurrence);
    }
}
