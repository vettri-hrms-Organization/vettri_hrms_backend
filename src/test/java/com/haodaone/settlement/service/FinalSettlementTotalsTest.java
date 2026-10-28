package com.haodaone.settlement.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.dto.LeavePolicyDTOs;
import com.haodaone.leave.service.LeaveEncashmentService;
import com.haodaone.salary.adjustment.PayrollAdjustment;
import com.haodaone.salary.adjustment.PayrollAdjustmentProvider;
import com.haodaone.salary.entity.*;
import com.haodaone.salary.repository.EmployeeTaxDeclarationRepository;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.settlement.entity.FinalSettlement;
import com.haodaone.settlement.entity.FinalSettlementLine;
import com.haodaone.settlement.entity.SettlementSettings;
import com.haodaone.settlement.repository.FinalSettlementRepository;
import com.haodaone.settlement.repository.SettlementSettingsRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** End-to-end F&F statement maths with mocked data sources (no TDS: statutory engine absent). */
class FinalSettlementTotalsTest {

    @Test
    void statementIncludesProratedSalaryEncashmentGratuityReimbursementAndNoticeRecovery() {
        SalaryStructureRepository structures = mock(SalaryStructureRepository.class);
        PayrollItemRepository items = mock(PayrollItemRepository.class);
        LeaveEncashmentService encashment = mock(LeaveEncashmentService.class);
        PayrollAdjustmentProvider provider = mock(PayrollAdjustmentProvider.class);
        FinalSettlementService service = new FinalSettlementService(mock(FinalSettlementRepository.class),
                mock(SettlementSettingsRepository.class), mock(EmployeeRepository.class), mock(CompanyRepository.class),
                structures, items, mock(EmployeeTaxDeclarationRepository.class), mock(AuditLogService.class),
                mock(AuthorizationService.class));
        service.setLeaveEncashmentService(encashment);
        service.setAdjustmentProviders(List.of(provider));

        Company company = new Company();
        company.setId(42L);
        Employee employee = new Employee();
        employee.setId(100L);
        employee.setCompany(company);
        employee.setFirstName("Ravi");
        employee.setLastName("Kumar");
        employee.setDateOfJoining(LocalDate.of(2019, 1, 1));
        employee.setStatus(EmploymentStatus.RESIGNED);

        SalaryComponents c = new SalaryComponents();
        c.setBasicSalary(new BigDecimal("30000"));
        c.setHra(new BigDecimal("15000"));
        c.setSpecialAllowance(new BigDecimal("15000"));
        c.setPf(new BigDecimal("1800"));
        SalaryStructure structure = new SalaryStructure();
        structure.setEmployee(employee);
        structure.setComponents(c);
        when(structures.findByEmployee_Company_IdAndEmployeeIdAndActiveTrueAndDeletedFalse(42L, 100L)).thenReturn(Optional.of(structure));

        // September 2026 already paid through payroll -> only October is settled.
        PayrollRun run = new PayrollRun();
        run.setPeriodYear(2026);
        run.setPeriodMonth(9);
        run.setStatus(PayrollRunStatus.PAID);
        PayrollItem paid = new PayrollItem();
        paid.setPayrollRun(run);
        paid.setStatus(PayrollItemStatus.PAID);
        when(items.findByEmployee_Company_IdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(42L, 100L)).thenReturn(List.of(paid));
        when(items.findApprovedUnpaidLeavesOverlapping(eq(42L), any(), any())).thenReturn(List.of());

        when(encashment.finalSettlementPreview(100L)).thenReturn(new LeavePolicyDTOs.FinalSettlementPreview(100L, "Ravi Kumar",
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15), true,
                List.of(new LeavePolicyDTOs.FinalSettlementLine(1L, "Earned Leave", "BASIC", 10, 10,
                        new BigDecimal("1000.00"), new BigDecimal("10000.00"))),
                new BigDecimal("10000.00"), "note"));
        when(provider.adjustmentsFor(eq(42L), eq(100L), any())).thenReturn(List.of(
                new PayrollAdjustment("REIMBURSEMENT", "Reimbursement - Travel", new BigDecimal("2500"), false, "EXPENSE_CLAIM", 9L)));
        when(provider.sourceTypes()).thenReturn(Set.of("EXPENSE_CLAIM"));

        FinalSettlement s = new FinalSettlement();
        s.setCompany(company);
        s.setEmployee(employee);
        s.setLastWorkingDate(LocalDate.of(2026, 10, 15));
        s.setResignationDate(LocalDate.of(2026, 10, 1));
        s.setNoticePeriodDays(30);
        s.setNoticeRecoveryBasis(SettlementSettings.BASIS_BASIC);
        FinalSettlementLine advance = new FinalSettlementLine();
        advance.setKind(FinalSettlementLine.DEDUCTION);
        advance.setCode("ADVANCE");
        advance.setLabel("Salary advance");
        advance.setAmount(new BigDecimal("5000"));
        advance.setManual(true);
        s.addLine(advance);

        service.recompute(s, new SettlementSettings(), null);

        // Salary: 15 of 31 days of 60,000 = 14516.13 + 7258.06 + 7258.06
        assertThat(amount(s, "SALARY")).isEqualByComparingTo("29032.25");
        assertThat(amount(s, "LEAVE_ENCASHMENT")).isEqualByComparingTo("10000.00");
        // 7y 9m 15d -> 8 years: 15 x 30000 x 8 / 26 = 138461.54 -> 138462
        assertThat(s.getGratuityYears()).isEqualTo(8);
        assertThat(amount(s, "GRATUITY")).isEqualByComparingTo("138462");
        assertThat(amount(s, "REIMBURSEMENT")).isEqualByComparingTo("2500");
        // Notice: served 1-15 Oct = 15 days, shortfall 15 x 30000 / 30
        assertThat(s.getNoticeServedDays()).isEqualTo(15);
        assertThat(s.getNoticeShortfallDays()).isEqualTo(15);
        assertThat(amount(s, "NOTICE_RECOVERY")).isEqualByComparingTo("15000.00");
        assertThat(amount(s, "PF")).isEqualByComparingTo("1800");
        assertThat(amount(s, "ADVANCE")).isEqualByComparingTo("5000");

        assertThat(s.getTotalEarnings()).isEqualByComparingTo("179994.25");
        assertThat(s.getTotalDeductions()).isEqualByComparingTo("21800.00");
        assertThat(s.getNetPayable()).isEqualByComparingTo("158194.25");
        // Only salary is taxable: gratuity (< 20L) and encashment (< 25L) are exempt, reimbursement is not income.
        assertThat(s.getTaxableAmount()).isEqualByComparingTo("29032.25");
        assertThat(s.getTdsAmount()).isEqualByComparingTo("0");

        // Waiving notice drops the recovery.
        s.setNoticeWaived(true);
        service.recompute(s, new SettlementSettings(), null);
        assertThat(s.getLines()).noneMatch(l -> "NOTICE_RECOVERY".equals(l.getCode()));
        assertThat(s.getNetPayable()).isEqualByComparingTo("173194.25");
        assertThat(s.getLines()).anyMatch(l -> "ADVANCE".equals(l.getCode())); // manual line kept
    }

    @Test
    void noticeMathEdges() {
        assertThat(SettlementMath.notice(null, LocalDate.of(2026, 10, 15), 30, false, null).shortfallDays()).isZero();
        assertThat(SettlementMath.notice(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 15), 30, false, null).shortfallDays()).isZero();
        assertThat(SettlementMath.notice(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), 30, false, 7).shortfallDays()).isEqualTo(7);
        assertThat(SettlementMath.noticeRecovery(10, new BigDecimal("60000"))).isEqualByComparingTo("20000.00");
        assertThat(SettlementMath.taxableLeaveEncashment(new BigDecimal("2600000"), SettlementMath.LEAVE_ENCASHMENT_EXEMPTION))
                .isEqualByComparingTo("100000");
        assertThat(SettlementMath.taxableGratuity(new BigDecimal("2100000"))).isEqualByComparingTo("100000");
        assertThat(SettlementMath.daysAfterExit(java.time.YearMonth.of(2026, 10), LocalDate.of(2026, 10, 31))).isZero();
    }

    private static BigDecimal amount(FinalSettlement s, String code) {
        return s.getLines().stream().filter(l -> code.equals(l.getCode())).map(FinalSettlementLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
