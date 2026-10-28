package com.haodaone.leave.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.dto.ApplyLeaveRequest;
import com.haodaone.leave.entity.Holiday;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.repository.LeaveBalanceRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.leave.repository.OptionalHolidaySelectionRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** Location-specific and optional holidays change how many leave days a request costs. */
class LocationHolidayLeaveCountTest {

    private static final LocalDate WED = LocalDate.of(2026, 10, 7);
    private static final LocalDate THU = LocalDate.of(2026, 10, 8);
    private static final LocalDate FRI = LocalDate.of(2026, 10, 9);

    private final LeaveRequestRepository leaveRequestRepository = mock(LeaveRequestRepository.class);
    private final LeaveTypeRepository leaveTypeRepository = mock(LeaveTypeRepository.class);
    private final LeaveBalanceRepository leaveBalanceRepository = mock(LeaveBalanceRepository.class);
    private final HolidayRepository holidayRepository = mock(HolidayRepository.class);
    private final OptionalHolidaySelectionRepository selectionRepository = mock(OptionalHolidaySelectionRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private LeaveRequestService service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(42L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice", "pw",
                List.of(new SimpleGrantedAuthority("SELF_LEAVE_APPLY"))));
        service = new LeaveRequestService(leaveRequestRepository, leaveTypeRepository, leaveBalanceRepository, holidayRepository,
                employeeRepository, mock(AuditLogService.class), mock(AuthorizationService.class));
        service.setHolidayCalendarService(new HolidayCalendarService(holidayRepository, selectionRepository));

        Company company = new Company();
        company.setId(42L);
        employee = new Employee();
        employee.setId(100L);
        employee.setCompany(company);
        LeaveType casual = new LeaveType();
        casual.setId(7L);
        casual.setName("Casual Leave");
        casual.setActive(true);
        casual.setDefaultDaysPerYear(12);

        lenient().when(employeeRepository.findByUser_UsernameAndDeletedFalse("alice")).thenReturn(Optional.of(employee));
        lenient().when(leaveTypeRepository.findByIdAndCompany_IdAndDeletedFalse(7L, 42L)).thenReturn(Optional.of(casual));
        lenient().when(leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(42L)).thenReturn(List.of(casual));
        lenient().when(leaveRequestRepository.findOverlapping(anyLong(), any(), any())).thenReturn(List.of());
        lenient().when(leaveRequestRepository.sumApprovedDays(anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(0.0);
        lenient().when(leaveBalanceRepository.findByEmployee_Company_IdAndEmployeeIdAndLeaveTypeIdAndYear(anyLong(), anyLong(), anyLong(), anyInt()))
                .thenReturn(Optional.empty());

        Holiday chennaiOnly = holiday(1L, "Local festival", THU, Holiday.MANDATORY, Set.of(5L));
        Holiday optional = holiday(2L, "Restricted holiday", FRI, Holiday.OPTIONAL, Set.of());
        lenient().when(holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(eq(42L), any(), any()))
                .thenReturn(List.of(chennaiOnly, optional));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void locationHolidayIsSkippedOnlyForEmployeesAtThatLocation() {
        lenient().when(selectionRepository.findPickedPairs(eq(42L), anyCollection(), anyCollection())).thenReturn(List.of());

        employee.setOfficeLocationId(5L);
        assertThat(service.preview(request(WED, FRI)).requestedDays()).isEqualTo(2.0); // Thu is their holiday

        employee.setOfficeLocationId(6L);
        assertThat(service.preview(request(WED, FRI)).requestedDays()).isEqualTo(3.0);

        employee.setOfficeLocationId(null); // no location: only company-wide holidays
        assertThat(service.preview(request(WED, FRI)).requestedDays()).isEqualTo(3.0);
    }

    @Test
    void pickedOptionalHolidayIsNotChargedAsLeave() {
        employee.setOfficeLocationId(6L);
        List<Object[]> picked = List.<Object[]>of(new Object[]{100L, 2L});
        lenient().when(selectionRepository.findPickedPairs(eq(42L), anyCollection(), anyCollection())).thenReturn(picked);

        assertThat(service.preview(request(WED, FRI)).requestedDays()).isEqualTo(2.0); // Fri picked
    }

    private static ApplyLeaveRequest request(LocalDate from, LocalDate to) {
        ApplyLeaveRequest r = new ApplyLeaveRequest();
        r.setEmployeeId(100L);
        r.setLeaveTypeId(7L);
        r.setStartDate(from);
        r.setEndDate(to);
        return r;
    }

    static Holiday holiday(Long id, String name, LocalDate date, String type, Set<Long> locations) {
        Holiday h = new Holiday();
        h.setId(id);
        h.setName(name);
        h.setDate(date);
        h.setHolidayType(type);
        h.setLocationIds(locations);
        return h;
    }
}
