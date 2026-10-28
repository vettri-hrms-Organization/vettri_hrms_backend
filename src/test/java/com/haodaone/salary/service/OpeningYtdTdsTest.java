package com.haodaone.salary.service;

import com.haodaone.attendance.repository.OfficeLocationRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.EmployeeTaxDeclarationRepository;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.StatutorySettingsRepository;
import com.haodaone.salary.statutory.IncomeTaxRates.Regime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** A mid-year switcher: imported opening YTD income and TDS must flow into the TDS projection. */
class OpeningYtdTdsTest {

    private static final long COMPANY = 1L;
    private static final int FY = 2026;

    private StatutoryPayrollEngine engine;
    private Employee employee;
    private SalaryStructure structure;
    private final OpeningYtdProvider.OpeningYtd opening = new OpeningYtdProvider.OpeningYtd(
            new BigDecimal("1200000"), new BigDecimal("1200000"), BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("100000"), 6, BigDecimal.ZERO, BigDecimal.ZERO);

    @BeforeEach
    void setUp() {
        engine = new StatutoryPayrollEngine(mock(StatutorySettingsRepository.class), mock(OfficeLocationRepository.class),
                mock(PayrollItemRepository.class), mock(EmployeeTaxDeclarationRepository.class));
        employee = new Employee();
        employee.setId(7L);
        employee.setDateOfJoining(LocalDate.of(2020, 1, 1));
        SalaryComponents components = new SalaryComponents();
        components.setBasicSalary(new BigDecimal("100000"));
        components.setSpecialAllowance(new BigDecimal("100000"));
        structure = new SalaryStructure();
        structure.setEmployee(employee);
        structure.setComponents(components);
        structure.setAutoStatutory(false);
        structure.recalculate();
    }

    private void withOpening() {
        engine.setOpeningYtdProvider((companyId, employeeId, fy) ->
                companyId == COMPANY && employeeId.equals(7L) && fy == FY ? Optional.of(opening) : Optional.empty());
    }

    @Test
    void projectionAddsOpeningIncomeAndTds() {
        var without = engine.project(COMPANY, employee, structure, FY, YearMonth.of(2026, 10), null, Regime.NEW);
        assertEquals(0, new BigDecimal("1200000").compareTo(without.result().grossSalary()));
        assertEquals(0, BigDecimal.ZERO.compareTo(without.tdsDeductedSoFar()));

        withOpening();
        var with = engine.project(COMPANY, employee, structure, FY, YearMonth.of(2026, 10), null, Regime.NEW);
        // 6 projected months (Oct-Mar) x 2,00,000 + 12,00,000 already paid before go-live.
        assertEquals(6, with.monthsProjected());
        assertEquals(0, new BigDecimal("2400000").compareTo(with.result().grossSalary()));
        assertEquals(0, new BigDecimal("100000").compareTo(with.tdsDeductedSoFar()));
        assertTrue(with.result().totalTax().compareTo(without.result().totalTax()) > 0);
        assertEquals(opening, engine.openingFor(COMPANY, 7L, FY));
        assertNull(engine.openingFor(COMPANY, 7L, FY - 1));
    }

    @Test
    void monthsCoveredByOpeningAreNotProjectedAgain() {
        withOpening();
        // As of July, Apr-Sep are covered by the opening figures, so only Oct-Mar are projected.
        var projection = engine.project(COMPANY, employee, structure, FY, YearMonth.of(2026, 7), null, Regime.NEW);
        assertEquals(6, projection.monthsProjected());
        assertEquals(0, new BigDecimal("2400000").compareTo(projection.result().grossSalary()));
    }

    @Test
    void monthlyTdsSpreadsTaxNetOfOpeningTdsOverRemainingMonths() {
        YearMonth october = YearMonth.of(2026, 10);
        BigDecimal tdsWithout = engine.monthlyTds(item(), structure, october, List.of(), COMPANY);
        BigDecimal taxWithout = engine.project(COMPANY, employee, structure, FY, october, null, Regime.NEW).result().totalTax();
        assertEquals(0, expected(taxWithout, BigDecimal.ZERO).compareTo(tdsWithout));

        withOpening();
        BigDecimal tdsWith = engine.monthlyTds(item(), structure, october, List.of(), COMPANY);
        BigDecimal taxWith = engine.project(COMPANY, employee, structure, FY, october, null, Regime.NEW).result().totalTax();
        // Same annual income as the projection (Oct item + 5 future months + opening); opening TDS is credited.
        assertEquals(0, expected(taxWith, new BigDecimal("100000")).compareTo(tdsWith));
        assertTrue(tdsWith.compareTo(tdsWithout) > 0);
    }

    private static BigDecimal expected(BigDecimal annualTax, BigDecimal alreadyDeducted) {
        BigDecimal balance = annualTax.subtract(alreadyDeducted);
        if (balance.signum() <= 0) return BigDecimal.ZERO;
        return balance.divide(BigDecimal.valueOf(6), 0, RoundingMode.HALF_UP);
    }

    private PayrollItem item() {
        PayrollItem item = new PayrollItem();
        item.setEmployee(employee);
        SalaryComponents components = new SalaryComponents();
        components.setBasicSalary(new BigDecimal("100000"));
        components.setSpecialAllowance(new BigDecimal("100000"));
        item.setComponents(components);
        item.recalculate();
        return item;
    }
}
