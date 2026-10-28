package com.haodaone.leave.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.dto.ApplyLeaveRequest;
import com.haodaone.leave.dto.LeaveRequestDTO;
import com.haodaone.leave.entity.Holiday;
import com.haodaone.leave.entity.LeaveRequest;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.repository.LeaveBalanceRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveHalfDayAndWorkingWeekTest {

    /** Thursday. */
    private static final LocalDate WORKDAY = LocalDate.of(2026, 10, 8);

    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private LeaveBalanceRepository leaveBalanceRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private AuthorizationService authorizationService;

    @InjectMocks
    private LeaveRequestService leaveRequestService;

    private Company company;
    private Employee employee;
    private LeaveType leaveType;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(42L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "pw",
                        List.of(new SimpleGrantedAuthority("SELF_LEAVE_APPLY"))));
        company = new Company();
        company.setId(42L);
        employee = new Employee();
        employee.setId(100L);
        employee.setFirstName("Alice");
        employee.setLastName("Example");
        employee.setCompany(company);
        leaveType = new LeaveType();
        leaveType.setId(7L);
        leaveType.setName("Casual Leave");
        leaveType.setActive(true);
        leaveType.setDefaultDaysPerYear(12);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void applyHalfDayStoresSessionAndHalfADay() {
        stubLookups();
        when(leaveRequestRepository.findOverlapping(100L, WORKDAY, WORKDAY)).thenReturn(List.of());
        stubBalanceAndCalendar(WORKDAY, WORKDAY);
        stubSave();

        LeaveRequestDTO created = leaveRequestService.apply(halfDay(WORKDAY, "first_half"));

        assertThat(created.getDays()).isEqualTo(0.5);
        assertThat(created.isHalfDay()).isTrue();
        assertThat(created.getHalfDaySession()).isEqualTo("FIRST_HALF");
    }

    @Test
    void secondHalfIsAllowedWhenFirstHalfAlreadyBooked() {
        stubLookups();
        when(leaveRequestRepository.findOverlapping(100L, WORKDAY, WORKDAY))
                .thenReturn(List.of(existingHalf("FIRST_HALF")));
        stubBalanceAndCalendar(WORKDAY, WORKDAY);
        stubSave();

        LeaveRequestDTO created = leaveRequestService.apply(halfDay(WORKDAY, "SECOND_HALF"));

        assertThat(created.getDays()).isEqualTo(0.5);
        assertThat(created.getHalfDaySession()).isEqualTo("SECOND_HALF");
    }

    @Test
    void sameHalfTwiceIsRejected() {
        stubLookups();
        when(leaveRequestRepository.findOverlapping(100L, WORKDAY, WORKDAY))
                .thenReturn(List.of(existingHalf("FIRST_HALF")));

        assertThatThrownBy(() -> leaveRequestService.apply(halfDay(WORKDAY, "FIRST_HALF")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("overlapping");
    }

    @Test
    void fullDayIsRejectedWhenHalfDayAlreadyBooked() {
        stubLookups();
        when(leaveRequestRepository.findOverlapping(100L, WORKDAY, WORKDAY))
                .thenReturn(List.of(existingHalf("FIRST_HALF")));
        ApplyLeaveRequest fullDay = request(WORKDAY, WORKDAY);

        assertThatThrownBy(() -> leaveRequestService.apply(fullDay))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("overlapping");
    }

    @Test
    void halfDayRequiresASingleDate() {
        stubLookups();
        ApplyLeaveRequest request = halfDay(WORKDAY, "FIRST_HALF");
        request.setEndDate(WORKDAY.plusDays(1));

        assertThatThrownBy(() -> leaveRequestService.apply(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("same date");
    }

    @Test
    void halfDayOnWeeklyOffIsRejected() {
        LocalDate sunday = LocalDate.of(2026, 10, 11);
        stubLookups();
        when(leaveRequestRepository.findOverlapping(100L, sunday, sunday)).thenReturn(List.of());
        when(holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(42L, sunday, sunday))
                .thenReturn(List.<Holiday>of());

        assertThatThrownBy(() -> leaveRequestService.apply(halfDay(sunday, "FIRST_HALF")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("working day");
    }

    @Test
    void mondayToSaturdayCompanyCountsSaturdayAsALeaveDay() {
        company.setWeeklyOffDays("SUNDAY");
        LocalDate friday = LocalDate.of(2026, 10, 9);
        LocalDate monday = LocalDate.of(2026, 10, 12);
        stubLookups();
        when(leaveRequestRepository.findOverlapping(100L, friday, monday)).thenReturn(List.of());
        stubBalanceAndCalendar(friday, monday);

        var preview = leaveRequestService.preview(request(friday, monday));

        assertThat(preview.requestedDays()).isEqualTo(3.0); // Fri, Sat, Mon
    }

    @Test
    void secondAndFourthSaturdayOffSkipsOnlyThoseSaturdays() {
        company.setWeeklyOffDays("SUNDAY,SATURDAY#2,SATURDAY#4");
        LocalDate firstSaturday = LocalDate.of(2026, 10, 3);
        LocalDate secondSaturday = LocalDate.of(2026, 10, 10);
        stubLookups();
        when(leaveRequestRepository.findOverlapping(100L, firstSaturday, secondSaturday)).thenReturn(List.of());
        stubBalanceAndCalendar(firstSaturday, secondSaturday);

        var preview = leaveRequestService.preview(request(firstSaturday, secondSaturday));

        // Sat 3 (1st Saturday, working), Mon 5 - Fri 9; Sun 4 and Sat 10 (2nd Saturday) are off.
        assertThat(preview.requestedDays()).isEqualTo(6.0);
    }

    private void stubLookups() {
        when(employeeRepository.findByUser_UsernameAndDeletedFalse("alice")).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findByIdAndCompany_IdAndDeletedFalse(7L, 42L)).thenReturn(Optional.of(leaveType));
    }

    private void stubBalanceAndCalendar(LocalDate start, LocalDate end) {
        when(leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(42L)).thenReturn(List.of(leaveType));
        when(leaveRequestRepository.sumApprovedDays(42L, 100L, 7L, start.getYear())).thenReturn(0.0);
        when(leaveBalanceRepository.findByEmployee_Company_IdAndEmployeeIdAndLeaveTypeIdAndYear(
                42L, 100L, 7L, start.getYear())).thenReturn(Optional.empty());
        when(holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(42L, start, end))
                .thenReturn(List.<Holiday>of());
    }

    private void stubSave() {
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(900L);
            return saved;
        });
    }

    private ApplyLeaveRequest request(LocalDate start, LocalDate end) {
        ApplyLeaveRequest request = new ApplyLeaveRequest();
        request.setEmployeeId(100L);
        request.setLeaveTypeId(7L);
        request.setStartDate(start);
        request.setEndDate(end);
        return request;
    }

    private ApplyLeaveRequest halfDay(LocalDate date, String session) {
        ApplyLeaveRequest request = request(date, date);
        request.setHalfDay(true);
        request.setHalfDaySession(session);
        return request;
    }

    private LeaveRequest existingHalf(String session) {
        LeaveRequest existing = new LeaveRequest();
        existing.setId(55L);
        existing.setEmployee(employee);
        existing.setLeaveType(leaveType);
        existing.setStartDate(WORKDAY);
        existing.setEndDate(WORKDAY);
        existing.setDays(0.5);
        existing.setHalfDay(true);
        existing.setHalfDaySession(session);
        existing.setStatus("PENDING");
        return existing;
    }
}
