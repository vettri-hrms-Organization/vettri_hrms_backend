package com.haodaone.leave.service;

import com.haodaone.audit.service.AuditLogService;
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
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private HolidayRepository holidayRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private LeaveRequestService leaveRequestService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(42L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "pw",
                        List.of(new SimpleGrantedAuthority("SELF_LEAVE_APPLY"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void applyRejectsSelfServiceRequestForAnotherEmployee() {
        Employee loggedIn = new Employee();
        loggedIn.setId(100L);
        loggedIn.setFirstName("Alice");
        loggedIn.setLastName("Example");
        Company company = new Company();
        company.setId(42L);
        loggedIn.setCompany(company);

        Employee otherEmployee = new Employee();
        otherEmployee.setId(200L);
        otherEmployee.setFirstName("Mallory");
        otherEmployee.setLastName("Example");
        otherEmployee.setCompany(company);

        LeaveType leaveType = new LeaveType();
        leaveType.setId(7L);
        leaveType.setName("Casual Leave");
        leaveType.setActive(true);
        leaveType.setDefaultDaysPerYear(12);

        ApplyLeaveRequest request = new ApplyLeaveRequest();
        request.setEmployeeId(200L);
        request.setLeaveTypeId(7L);
        request.setStartDate(LocalDate.of(2025, 5, 1));
        request.setEndDate(LocalDate.of(2025, 5, 2));
        request.setReason("Personal");

        when(employeeRepository.findByUser_UsernameAndDeletedFalse("alice")).thenReturn(Optional.of(loggedIn));

        assertThatThrownBy(() -> leaveRequestService.apply(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("LEAVE_APPLY is not authorized for this employee");
    }

    @Test
    void previewAllowsPastCasualLeaveWhenExistingLeaveRulesPass() {
        Company company = new Company();
        company.setId(42L);
        Employee employee = new Employee();
        employee.setId(100L);
        employee.setCompany(company);
        LeaveType leaveType = new LeaveType();
        leaveType.setId(7L);
        leaveType.setName("Casual Leave");
        leaveType.setActive(true);
        leaveType.setDefaultDaysPerYear(12);
        LocalDate backdated = LocalDate.of(2026, 10, 8);

        ApplyLeaveRequest request = new ApplyLeaveRequest();
        request.setEmployeeId(100L);
        request.setLeaveTypeId(7L);
        request.setStartDate(backdated);
        request.setEndDate(backdated);

        when(employeeRepository.findByUser_UsernameAndDeletedFalse("alice")).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findByIdAndCompany_IdAndDeletedFalse(7L, 42L))
                .thenReturn(Optional.of(leaveType));
        when(leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(42L))
                .thenReturn(List.of(leaveType));
        when(leaveRequestRepository.findOverlapping(100L, backdated, backdated)).thenReturn(List.of());
        when(leaveRequestRepository.sumApprovedDays(42L, 100L, 7L, 2026)).thenReturn(0.0);
        when(leaveBalanceRepository.findByEmployee_Company_IdAndEmployeeIdAndLeaveTypeIdAndYear(
                42L, 100L, 7L, 2026)).thenReturn(Optional.empty());
        when(holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(42L, backdated, backdated))
                .thenReturn(List.<Holiday>of());

        var preview = leaveRequestService.preview(request);

        assertThat(preview.startDate()).isEqualTo(backdated);
        assertThat(preview.endDate()).isEqualTo(backdated);
        assertThat(preview.requestedDays()).isEqualTo(1.0);
    }

    @Test
    void applySavesBackdatedCasualLeaveAfterExistingPolicyValidation() {
        Company company = new Company();
        company.setId(42L);
        Employee employee = new Employee();
        employee.setId(100L);
        employee.setEmployeeCode("EMP-100");
        employee.setFirstName("Alice");
        employee.setLastName("Example");
        employee.setCompany(company);
        LeaveType leaveType = new LeaveType();
        leaveType.setId(7L);
        leaveType.setName("Casual Leave");
        leaveType.setActive(true);
        leaveType.setDefaultDaysPerYear(12);
        LocalDate backdated = LocalDate.of(2026, 10, 8);

        ApplyLeaveRequest request = new ApplyLeaveRequest();
        request.setEmployeeId(100L);
        request.setLeaveTypeId(7L);
        request.setStartDate(backdated);
        request.setEndDate(backdated);

        when(employeeRepository.findByUser_UsernameAndDeletedFalse("alice")).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findByIdAndCompany_IdAndDeletedFalse(7L, 42L))
                .thenReturn(Optional.of(leaveType));
        when(leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(42L))
                .thenReturn(List.of(leaveType));
        when(leaveRequestRepository.findOverlapping(100L, backdated, backdated)).thenReturn(List.of());
        when(leaveRequestRepository.sumApprovedDays(42L, 100L, 7L, 2026)).thenReturn(0.0);
        when(leaveBalanceRepository.findByEmployee_Company_IdAndEmployeeIdAndLeaveTypeIdAndYear(
                42L, 100L, 7L, 2026)).thenReturn(Optional.empty());
        when(holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(42L, backdated, backdated))
                .thenReturn(List.of());
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest saved = invocation.getArgument(0);
            saved.setId(876L);
            return saved;
        });

        LeaveRequestDTO created = leaveRequestService.apply(request);

        assertThat(created.getId()).isEqualTo(876L);
        assertThat(created.getStatus()).isEqualTo("PENDING");
        assertThat(created.getStartDate()).isEqualTo(backdated);
        assertThat(created.getEndDate()).isEqualTo(backdated);
    }

    @Test
    void leaveApplyPermissionAloneCannotCancelAnotherEmployeesRequest() {
        Company company = new Company();
        company.setId(42L);
        Employee loggedIn = new Employee();
        loggedIn.setId(100L);
        loggedIn.setCompany(company);
        Employee requestOwner = new Employee();
        requestOwner.setId(200L);
        requestOwner.setCompany(company);
        LeaveRequest request = new LeaveRequest();
        request.setId(99L);
        request.setCompany(company);
        request.setEmployee(requestOwner);
        request.setStatus("PENDING");
        request.setStartDate(LocalDate.now().plusDays(5));
        request.setEndDate(LocalDate.now().plusDays(6));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "pw",
                        List.of(new SimpleGrantedAuthority("LEAVE_APPLY"))));
        when(employeeRepository.findByUser_UsernameAndDeletedFalse("alice")).thenReturn(Optional.of(loggedIn));
        when(leaveRequestRepository.findById(99L)).thenReturn(Optional.of(request));
        when(authorizationService.isAllowed("LEAVE_APPROVE", "EMPLOYEE", 200L)).thenReturn(false);

        assertThatThrownBy(() -> leaveRequestService.cancel(99L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("request owner or an authorized leave approver");
    }

    @Test
    void dtoMapsEmployeeAndLeaveTypeFieldsForLeaveRequests() {
        Employee employee = new Employee();
        employee.setId(4268L);
        employee.setEmployeeCode("EMP-4268");
        employee.setFirstName("Jamie");
        employee.setLastName("Wells");

        LeaveType leaveType = new LeaveType();
        leaveType.setId(12L);
        leaveType.setName("Earned Leave");

        LeaveRequest request = new LeaveRequest();
        request.setId(99L);
        request.setEmployee(employee);
        request.setLeaveType(leaveType);
        request.setStartDate(LocalDate.of(2025, 5, 10));
        request.setEndDate(LocalDate.of(2025, 5, 12));
        request.setDays(2.0);
        request.setStatus("PENDING");

        LeaveRequestDTO dto = LeaveRequestDTO.from(request);

        assertThat(dto.getEmployeeId()).isEqualTo(4268L);
        assertThat(dto.getEmployeeCode()).isEqualTo("EMP-4268");
        assertThat(dto.getEmployeeName()).isEqualTo("Jamie Wells");
        assertThat(dto.getLeaveTypeId()).isEqualTo(12L);
        assertThat(dto.getLeaveTypeName()).isEqualTo("Earned Leave");
        assertThat(dto.getStatus()).isEqualTo("PENDING");
    }
}
