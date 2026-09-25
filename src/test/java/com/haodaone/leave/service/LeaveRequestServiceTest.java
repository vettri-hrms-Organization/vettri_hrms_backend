package com.haodaone.leave.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.dto.ApplyLeaveRequest;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Employees can only apply leave for themselves");
    }
}
