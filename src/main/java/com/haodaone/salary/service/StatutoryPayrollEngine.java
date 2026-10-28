package com.haodaone.salary.service;

import com.haodaone.attendance.repository.OfficeLocationRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.salary.entity.EmployeeTaxDeclaration;
import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.PayrollRun;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.entity.StatutorySettings;
import com.haodaone.salary.repository.EmployeeTaxDeclarationRepository;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.StatutorySettingsRepository;
import com.haodaone.salary.statutory.IncomeTaxCalculator;
import com.haodaone.salary.statutory.IncomeTaxCalculator.TaxInput;
import com.haodaone.salary.statutory.IncomeTaxCalculator.TaxResult;
import com.haodaone.salary.statutory.IncomeTaxRates.Regime;
import com.haodaone.salary.statutory.ProfessionalTaxSlabs;
import com.haodaone.salary.statutory.StatutoryMath;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Auto-calculates PF, ESI, professional tax and TDS for one payroll line
 * whose salary structure has {@code autoStatutory = true}, and projects an
 * employee's annual income tax for the declaration screen and Form 16.
 *
 * TDS (monthly) = (projected annual tax - TDS already deducted this FY)
 *                 / months left in the FY including this one,
 * where projected annual taxable salary = taxable gross of finalized
 * (PROCESSED/PAID) items earlier this FY + this month's taxable gross +
 * the structure's full monthly gross x months after this one. Imported
 * opening YTD figures ({@link OpeningYtdProvider}: salary/TDS before go-live
 * and previous-employer Form 12B income/TDS) are added to both the income
 * and the TDS already deducted.
 *
 * ESI: covered when the structure's full monthly gross is at or below the
 * threshold. Once covered in a contribution period (Apr-Sep / Oct-Mar) the
 * employee stays covered until that period ends, even after a raise - we
 * detect this from a finalized item earlier in the same period with ESI.
 */
@Service
public class StatutoryPayrollEngine {

    private final StatutorySettingsRepository settingsRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final EmployeeTaxDeclarationRepository declarationRepository;

    public StatutoryPayrollEngine(StatutorySettingsRepository settingsRepository,
                                  OfficeLocationRepository officeLocationRepository,
                                  PayrollItemRepository payrollItemRepository,
                                  EmployeeTaxDeclarationRepository declarationRepository) {
        this.settingsRepository = settingsRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.declarationRepository = declarationRepository;
    }

    /** Opening YTD figures for mid-year switchers (data import module). Optional: null = no opening figures. */
    private OpeningYtdProvider openingYtdProvider;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setOpeningYtdProvider(OpeningYtdProvider openingYtdProvider) {
        this.openingYtdProvider = openingYtdProvider;
    }

    /** Opening YTD figures for the employee and FY, or null when none were imported. */
    public OpeningYtdProvider.OpeningYtd openingFor(Long companyId, Long employeeId, int fy) {
        if (openingYtdProvider == null || companyId == null || employeeId == null) return null;
        return openingYtdProvider.openingFor(companyId, employeeId, fy).orElse(null);
    }

    /** Adds the opening figures as one extra "month" of income (no basic/HRA split: HRA exemption ignores it). */
    private static void addOpening(List<MonthFigures> months, OpeningYtdProvider.OpeningYtd opening) {
        if (opening == null) return;
        months.add(new MonthFigures(opening.totalTaxable(), BigDecimal.ZERO, BigDecimal.ZERO,
                nz(opening.professionalTax()), nz(opening.pfEmployee())));
    }

    /** Company settings resolved once per run. */
    public record Context(Long companyId, StatutorySettings settings, String ptState) {
    }

    /** One month's salary figures, finalized or projected. */
    record MonthFigures(BigDecimal taxableGross, BigDecimal basic, BigDecimal hra, BigDecimal pt, BigDecimal pf) {
        static MonthFigures of(PayrollItem item) {
            SalaryComponents c = item.getComponents();
            return new MonthFigures(item.effectiveTaxableGross(), c.getBasicSalary(), c.getHra(),
                    c.getProfessionalTax(), c.getPf());
        }
    }

    /** Annual tax projection plus how much has already been deducted. */
    public record TaxProjection(int financialYear, TaxResult result, BigDecimal tdsDeductedSoFar,
                                int monthsProjected, int monthsFinalized) {
    }

    public Context contextFor(Long companyId) {
        StatutorySettings settings = settingsRepository.findByCompany_IdAndDeletedFalse(companyId)
                .orElseGet(StatutorySettings::new);
        return new Context(companyId, settings, resolvePtState(companyId, settings));
    }

    /** The configured PT state, else the first active office location whose state we have a table for. */
    public String resolvePtState(Long companyId, StatutorySettings settings) {
        if (settings.getPtState() != null && !settings.getPtState().isBlank()) return settings.getPtState();
        return officeLocationRepository.findAllByCompany_IdAndActiveTrueAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(office -> ProfessionalTaxSlabs.codeForStateName(office.getState()))
                .filter(code -> code != null)
                .findFirst().orElse(null);
    }

    /**
     * Overwrites pf/esi/professionalTax/tds on the item's (already prorated) components and fills the
     * employer contribution columns. Adjustments must already be attached. Recalculates the item.
     */
    public void apply(PayrollItem item, SalaryStructure structure, YearMonth period, Context ctx) {
        StatutorySettings s = ctx.settings();
        SalaryComponents c = item.getComponents();
        item.recalculate();
        item.setStatutoryAuto(true);

        List<PayrollItem> earlier = finalizedBefore(ctx.companyId(), item.getEmployee().getId(), period);

        // PF on prorated basic.
        StatutoryMath.PfResult pf = s.isPfEnabled() ? StatutoryMath.pf(c.getBasicSalary(), s) : StatutoryMath.PfResult.ZERO;
        c.setPf(pf.employeeShare());
        item.setPfWages(pf.pfWages());
        item.setEpsWages(pf.epsWages());
        item.setEdliWages(pf.edliWages());
        item.setEmployerEps(pf.employerEps());
        item.setEmployerEpf(pf.employerEpf());

        // ESI on the month's wages (taxable gross: reimbursements are not wages).
        StatutoryMath.EsiResult esi = StatutoryMath.EsiResult.NOT_APPLICABLE;
        if (s.isEsiEnabled()) {
            boolean eligible = StatutoryMath.esiCovered(structure.getComponents().grossSalary(), s)
                    || coveredEarlierInContributionPeriod(earlier, period);
            esi = StatutoryMath.esi(eligible, item.effectiveTaxableGross(), s);
        }
        c.setEsi(esi.employeeShare());
        item.setEmployerEsi(esi.employerShare());

        // Professional tax on the month's gross.
        BigDecimal pt = BigDecimal.ZERO;
        if (s.isPtEnabled()) {
            if (s.isPtFixedOverride()) {
                pt = item.getGrossSalary().signum() > 0 ? nz(s.getPtFixedAmount()) : BigDecimal.ZERO;
            } else {
                pt = ProfessionalTaxSlabs.monthly(ctx.ptState(), item.getGrossSalary(), period.getMonthValue(),
                        item.getEmployee().getGender());
            }
            item.setPtState(s.isPtFixedOverride() ? "FIXED" : ctx.ptState());
        }
        c.setProfessionalTax(pt);

        // TDS spread over the rest of the FY.
        BigDecimal tds = BigDecimal.ZERO;
        if (s.isTdsEnabled()) {
            tds = monthlyTds(item, structure, period, earlier, ctx.companyId());
        }
        c.setTds(tds);

        StatutoryMath.capDeductions(c, item.getGrossSalary());
        item.recalculate();
    }

    BigDecimal monthlyTds(PayrollItem item, SalaryStructure structure, YearMonth period, List<PayrollItem> earlier, Long companyId) {
        int fy = financialYearOf(period);
        int monthsAfter = monthsAfter(period, fy);
        List<MonthFigures> months = new java.util.ArrayList<>(earlier.stream().map(MonthFigures::of).toList());
        months.add(MonthFigures.of(item));
        SalaryComponents full = structure.getComponents();
        BigDecimal futurePt = item.getComponents().getProfessionalTax();
        BigDecimal futurePf = item.getComponents().getPf();
        for (int i = 0; i < monthsAfter; i++) {
            months.add(new MonthFigures(full.grossSalary(), full.getBasicSalary(), full.getHra(), futurePt, futurePf));
        }
        BigDecimal tdsSoFar = earlier.stream().map(p -> nz(p.getComponents().getTds())).reduce(BigDecimal.ZERO, BigDecimal::add);
        OpeningYtdProvider.OpeningYtd opening = openingFor(companyId, item.getEmployee().getId(), fy);
        if (opening != null) {
            addOpening(months, opening);
            tdsSoFar = tdsSoFar.add(opening.totalTds());
        }
        EmployeeTaxDeclaration declaration = declarationRepository
                .findByCompany_IdAndEmployee_IdAndFinancialYearAndDeletedFalse(companyId, item.getEmployee().getId(), fy)
                .orElse(null);
        TaxResult result = IncomeTaxCalculator.compute(input(fy, months, declaration, null));
        BigDecimal balance = result.totalTax().subtract(tdsSoFar);
        if (balance.signum() <= 0) return BigDecimal.ZERO;
        return balance.divide(BigDecimal.valueOf(monthsAfter + 1L), 0, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
    }

    /**
     * Annual tax for the declaration screen / Form 16: finalized items this FY plus the current structure
     * for every not-yet-paid month from {@code asOf} (or the FY start, whichever is later) to March.
     *
     * @param regimeOverride compute for this regime instead of the declaration's (for a side-by-side comparison)
     */
    public TaxProjection project(Long companyId, Employee employee, SalaryStructure structure, int fy,
                                 YearMonth asOf, EmployeeTaxDeclaration declaration, Regime regimeOverride) {
        List<PayrollItem> finalized = payrollItemRepository.findFinalizedForEmployeeInFinancialYear(companyId, employee.getId(), fy, fy + 1);
        List<MonthFigures> months = new java.util.ArrayList<>(finalized.stream().map(MonthFigures::of).toList());
        Set<YearMonth> paid = new HashSet<>();
        for (PayrollItem item : finalized) {
            PayrollRun run = item.getPayrollRun();
            paid.add(YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()));
        }
        int projected = 0;
        OpeningYtdProvider.OpeningYtd opening = openingFor(companyId, employee.getId(), fy);
        if (structure != null) {
            SalaryComponents full = structure.getComponents();
            Context ctx = structure.isAutoStatutory() ? contextFor(companyId) : null;
            BigDecimal monthlyPt = ctx != null ? estimateMonthlyPt(ctx, full.grossSalary(), employee) : full.getProfessionalTax();
            BigDecimal monthlyPf = ctx != null
                    ? (ctx.settings().isPfEnabled() ? StatutoryMath.pf(full.getBasicSalary(), ctx.settings()).employeeShare() : BigDecimal.ZERO)
                    : full.getPf();
            YearMonth start = YearMonth.of(fy, 4);
            if (asOf != null && asOf.isAfter(start)) start = asOf;
            if (employee.getDateOfJoining() != null && YearMonth.from(employee.getDateOfJoining()).isAfter(start)) {
                start = YearMonth.from(employee.getDateOfJoining());
            }
            if (opening != null && opening.monthsCovered() > 0) {
                // Months already paid outside Vettri (counted from April) are in the opening figures.
                YearMonth afterOpening = YearMonth.of(fy, 4).plusMonths(opening.monthsCovered());
                if (afterOpening.isAfter(start)) start = afterOpening;
            }
            for (YearMonth m = start; !m.isAfter(YearMonth.of(fy + 1, 3)); m = m.plusMonths(1)) {
                if (paid.contains(m)) continue;
                months.add(new MonthFigures(full.grossSalary(), full.getBasicSalary(), full.getHra(), monthlyPt, monthlyPf));
                projected++;
            }
        }
        BigDecimal tdsSoFar = finalized.stream().map(p -> nz(p.getComponents().getTds())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (opening != null) {
            addOpening(months, opening);
            tdsSoFar = tdsSoFar.add(opening.totalTds());
        }
        TaxResult result = IncomeTaxCalculator.compute(input(fy, months, declaration, regimeOverride));
        return new TaxProjection(fy, result, tdsSoFar, projected, finalized.size());
    }

    /** A typical (non-February) month's PT for a full-month gross, honouring the company's PT settings. */
    BigDecimal estimateMonthlyPt(Context ctx, BigDecimal monthlyGross, Employee employee) {
        StatutorySettings s = ctx.settings();
        if (!s.isPtEnabled() || monthlyGross == null || monthlyGross.signum() <= 0) return BigDecimal.ZERO;
        if (s.isPtFixedOverride()) return nz(s.getPtFixedAmount());
        return ProfessionalTaxSlabs.monthly(ctx.ptState(), monthlyGross, 1, employee.getGender());
    }

    static TaxInput input(int fy, List<MonthFigures> months, EmployeeTaxDeclaration d, Regime regimeOverride) {
        BigDecimal gross = BigDecimal.ZERO, basic = BigDecimal.ZERO, hra = BigDecimal.ZERO, pt = BigDecimal.ZERO,
                pf = BigDecimal.ZERO;
        for (MonthFigures m : months) {
            gross = gross.add(nz(m.taxableGross()));
            basic = basic.add(nz(m.basic()));
            hra = hra.add(nz(m.hra()));
            pt = pt.add(nz(m.pt()));
            pf = pf.add(nz(m.pf()));
        }
        Regime regime = regimeOverride != null ? regimeOverride : regimeOf(d);
        // Employee PF contribution qualifies under 80C (old regime) alongside declared investments.
        if (d == null) {
            return new TaxInput(fy, regime, gross, basic, hra, null, false, pt, pf, null, null, null, null);
        }
        return new TaxInput(fy, regime, gross, basic, hra, d.getRentPaid(), d.isMetroCity(), pt,
                nz(d.getSection80C()).add(pf), d.getSection80D(), d.getHomeLoanInterest(), d.getNps80ccd1b(), d.getOtherDeductions());
    }

    public static Regime regimeOf(EmployeeTaxDeclaration d) {
        return d != null && "OLD".equalsIgnoreCase(d.getRegime()) ? Regime.OLD : Regime.NEW;
    }

    private List<PayrollItem> finalizedBefore(Long companyId, Long employeeId, YearMonth period) {
        int fy = financialYearOf(period);
        return payrollItemRepository.findFinalizedForEmployeeInFinancialYear(companyId, employeeId, fy, fy + 1).stream()
                .filter(i -> YearMonth.of(i.getPayrollRun().getPeriodYear(), i.getPayrollRun().getPeriodMonth()).isBefore(period))
                .toList();
    }

    private static boolean coveredEarlierInContributionPeriod(List<PayrollItem> earlier, YearMonth period) {
        int startMonth = StatutoryMath.esiContributionPeriodStart(period.getMonthValue());
        YearMonth periodStart = startMonth == 10 && period.getMonthValue() <= 3
                ? YearMonth.of(period.getYear() - 1, 10)
                : YearMonth.of(period.getYear(), startMonth);
        return earlier.stream().anyMatch(i -> {
            YearMonth m = YearMonth.of(i.getPayrollRun().getPeriodYear(), i.getPayrollRun().getPeriodMonth());
            return !m.isBefore(periodStart) && nz(i.getComponents().getEsi()).signum() > 0;
        });
    }

    /** FY start year for a month: Apr 2026 - Mar 2027 is FY 2026. */
    public static int financialYearOf(YearMonth period) {
        return period.getMonthValue() >= 4 ? period.getYear() : period.getYear() - 1;
    }

    /** Months in the FY after {@code period} (0 for March). */
    static int monthsAfter(YearMonth period, int fy) {
        YearMonth march = YearMonth.of(fy + 1, 3);
        return (int) java.time.temporal.ChronoUnit.MONTHS.between(period, march);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
