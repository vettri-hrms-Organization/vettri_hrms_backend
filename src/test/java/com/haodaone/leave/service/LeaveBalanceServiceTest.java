package com.haodaone.leave.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.entity.LeaveBalance;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.CompOffRequestRepository;
import com.haodaone.leave.repository.LeaveBalanceAdjustmentRepository;
import com.haodaone.leave.repository.LeaveBalanceRepository;
import com.haodaone.leave.repository.LeaveEncashmentRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.leave.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaveBalanceServiceTest {

    private LeaveBalanceRepository balances;
    private LeaveRequestRepository requests;
    private LeaveBalanceAdjustmentRepository adjustments;
    private LeaveEncashmentRepository encashments;
    private LeaveBalanceService service;
    private Employee employee;
    private LeaveType earned;

    @BeforeEach
    void setUp() {
        balances = mock(LeaveBalanceRepository.class);
        requests = mock(LeaveRequestRepository.class);
        adjustments = mock(LeaveBalanceAdjustmentRepository.class);
        encashments = mock(LeaveEncashmentRepository.class);
        service = new LeaveBalanceService(mock(LeaveTypeRepository.class), balances, requests, adjustments, encashments,
                mock(CompOffRequestRepository.class), mock(EmployeeRepository.class), mock(AuditLogService.class));

        Company company = new Company();
        company.setId(1L);
        employee = new Employee();
        employee.setId(10L);
        employee.setCompany(company);
        employee.setDateOfJoining(LocalDate.of(2020, 1, 1));
        earned = new LeaveType();
        earned.setId(5L);
        earned.setName("Earned Leave");
        earned.setCode("EL");
        earned.setDefaultDaysPerYear(18);
        earned.setAccrualMode("MONTHLY");
        earned.setCarryForward(true);
        earned.setCarryForwardExpiryMonths(3);
    }

    @Test
    void balanceCombinesAccrualCarryExpiryAdjustmentsUsageAndEncashment() {
        LeaveBalance row = new LeaveBalance();
        row.setCarriedForwardDays(6);
        when(balances.findByEmployee_Company_IdAndEmployeeIdAndLeaveTypeIdAndYear(1L, 10L, 5L, 2026)).thenReturn(Optional.of(row));
        when(adjustments.sumDays(1L, 10L, 5L, 2026)).thenReturn(1.0);
        when(requests.sumApprovedDays(1L, 10L, 5L, 2026)).thenReturn(4.0);
        when(encashments.sumConsumedDays(1L, 10L, 5L, 2026)).thenReturn(2.0);
        // 2 days of leave before the carried days expired on 1 April
        when(requests.sumApprovedDaysBetween(eq(1L), eq(10L), eq(5L), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 3, 31)))).thenReturn(2.0);

        LeaveBalanceService.BalanceDetail d = service.compute(1L, employee, earned, 2026, LocalDate.of(2026, 6, 15));

        assertThat(d.accrued()).isEqualTo(9.0);          // 6 months x 1.5
        assertThat(d.annualEntitlement()).isEqualTo(18.0);
        assertThat(d.lapsed()).isEqualTo(4.0);           // 6 carried - 2 used before expiry
        assertThat(d.carryExpiry()).isEqualTo(LocalDate.of(2026, 4, 1));
        // 9 + 6 + 1 - 4 - 2 - 4
        assertThat(d.remaining()).isEqualTo(6.0);
        assertThat(service.toDto(d).getRemainingDays()).isEqualTo(6.0);
        assertThat(service.toDto(d).getAccrualMode()).isEqualTo("MONTHLY");
    }

    @Test
    void capLimitsAccrualButKeepsCarriedDays() {
        earned.setMaxBalanceDays(10.0);
        earned.setCarryForwardExpiryMonths(null);
        LeaveBalance row = new LeaveBalance();
        row.setCarriedForwardDays(8);
        when(balances.findByEmployee_Company_IdAndEmployeeIdAndLeaveTypeIdAndYear(anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(Optional.of(row));
        when(adjustments.sumDays(anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(0.0);
        when(requests.sumApprovedDays(anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(0.0);
        when(encashments.sumConsumedDays(anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(0.0);

        LeaveBalanceService.BalanceDetail d = service.compute(1L, employee, earned, 2026, LocalDate.of(2026, 12, 31));

        assertThat(d.accruedUncapped()).isEqualTo(18.0);
        assertThat(d.accrued()).isEqualTo(2.0);
        assertThat(d.remaining()).isEqualTo(10.0);
    }

    @Test
    void exitedEmployeesCountUntilLastWorkingDate() {
        employee.setStatus("Resigned");
        employee.setLastWorkingDate(LocalDate.of(2026, 3, 31));
        assertThat(LeaveBalanceService.isEmployedOn(employee, LocalDate.of(2026, 3, 31))).isTrue();
        assertThat(LeaveBalanceService.isEmployedOn(employee, LocalDate.of(2026, 4, 1))).isFalse();
    }
}
