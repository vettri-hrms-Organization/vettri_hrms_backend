package com.haodaone.salary;

import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.entity.StatutorySettings;
import com.haodaone.salary.statutory.ProfessionalTaxSlabs;
import com.haodaone.salary.statutory.StatutoryMath;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PF / ESI / PT arithmetic - pure, no Spring. */
class StatutoryMathTest {

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void pfIsCappedAtTheWageCeilingAndSplitIntoEpsAndEpf() {
        var pf = StatutoryMath.pf(new BigDecimal("30000"), new StatutorySettings());
        assertAmount("15000", pf.pfWages());
        assertAmount("15000", pf.epsWages());
        assertAmount("1800", pf.employeeShare());
        assertAmount("1250", pf.employerEps());   // 1,249.50 rounded
        assertAmount("550", pf.employerEpf());
    }

    @Test
    void pfBelowCeilingAndOnFullBasic() {
        var below = StatutoryMath.pf(new BigDecimal("10000"), new StatutorySettings());
        assertAmount("1200", below.employeeShare());
        assertAmount("833", below.employerEps());
        assertAmount("367", below.employerEpf());

        StatutorySettings full = new StatutorySettings();
        full.setPfOnFullBasic(true);
        var onFull = StatutoryMath.pf(new BigDecimal("30000"), full);
        assertAmount("30000", onFull.pfWages());
        assertAmount("15000", onFull.epsWages());    // EPS stays capped
        assertAmount("3600", onFull.employeeShare());
        assertAmount("1250", onFull.employerEps());
        assertAmount("2350", onFull.employerEpf());

        assertAmount("0", StatutoryMath.pf(BigDecimal.ZERO, full).employeeShare());
    }

    @Test
    void esiRoundsUpAndAppliesOnlyWhenCovered() {
        StatutorySettings s = new StatutorySettings();
        assertTrue(StatutoryMath.esiCovered(new BigDecimal("21000"), s));
        assertFalse(StatutoryMath.esiCovered(new BigDecimal("21000.01"), s));

        var esi = StatutoryMath.esi(true, new BigDecimal("18001"), s);
        assertAmount("136", esi.employeeShare());   // 135.0075 -> next rupee
        assertAmount("586", esi.employerShare());   // 585.0325 -> next rupee
        assertFalse(StatutoryMath.esi(false, new BigDecimal("18000"), s).applicable());
        assertEquals(4, StatutoryMath.esiContributionPeriodStart(9));
        assertEquals(10, StatutoryMath.esiContributionPeriodStart(2));
    }

    @Test
    void capTrimsOtherThenTdsThenPtThenEsiThenPf() {
        SalaryComponents c = new SalaryComponents();
        c.setPf(new BigDecimal("1800"));
        c.setEsi(new BigDecimal("100"));
        c.setProfessionalTax(new BigDecimal("200"));
        c.setTds(new BigDecimal("500"));
        c.setOtherDeductions(new BigDecimal("300"));
        StatutoryMath.capDeductions(c, new BigDecimal("2000"));
        assertAmount("0", c.getOtherDeductions());
        assertAmount("0", c.getTds());
        assertAmount("100", c.getProfessionalTax());
        assertAmount("100", c.getEsi());
        assertAmount("1800", c.getPf());
        assertAmount("2000", c.totalDeductions());
    }

    @Test
    void professionalTaxMonthlySlabsAndFebruaryTopUp() {
        assertAmount("0", ProfessionalTaxSlabs.monthly("KA", new BigDecimal("24999"), 11, null));
        assertAmount("200", ProfessionalTaxSlabs.monthly("KA", new BigDecimal("25000"), 11, null));
        assertAmount("300", ProfessionalTaxSlabs.monthly("KA", new BigDecimal("50000"), 2, null));

        assertAmount("175", ProfessionalTaxSlabs.monthly("MH", new BigDecimal("9000"), 11, "MALE"));
        assertAmount("200", ProfessionalTaxSlabs.monthly("MH", new BigDecimal("12000"), 11, "MALE"));
        assertAmount("300", ProfessionalTaxSlabs.monthly("MH", new BigDecimal("12000"), 2, "MALE"));
        assertAmount("0", ProfessionalTaxSlabs.monthly("MH", new BigDecimal("20000"), 11, "FEMALE"));
        assertAmount("300", ProfessionalTaxSlabs.monthly("MH", new BigDecimal("30000"), 2, "Female"));

        assertAmount("150", ProfessionalTaxSlabs.monthly("TG", new BigDecimal("18000"), 5, null));
        assertAmount("200", ProfessionalTaxSlabs.monthly("AP", new BigDecimal("25000"), 5, null));
        assertAmount("130", ProfessionalTaxSlabs.monthly("WB", new BigDecimal("20000"), 5, null));
        assertAmount("200", ProfessionalTaxSlabs.monthly("GJ", new BigDecimal("12001"), 5, null));
        assertAmount("0", ProfessionalTaxSlabs.monthly("NONE", new BigDecimal("500000"), 5, null));
        assertAmount("0", ProfessionalTaxSlabs.monthly(null, new BigDecimal("500000"), 5, null));
    }

    @Test
    void halfYearlyStatesSpreadTheHalfYearAmountExactly() {
        // Tamil Nadu, 50,000/month -> 3,00,000 per half year -> top slab 1,250 per half year.
        BigDecimal monthly = new BigDecimal("50000");
        BigDecimal halfYear = BigDecimal.ZERO;
        for (int month = 4; month <= 9; month++) {
            halfYear = halfYear.add(ProfessionalTaxSlabs.monthly("TN", monthly, month, null));
        }
        assertAmount("1250", halfYear);
        assertAmount("208", ProfessionalTaxSlabs.monthly("TN", monthly, 4, null));
        assertAmount("210", ProfessionalTaxSlabs.monthly("TN", monthly, 9, null));

        // Kerala, 6,000/month -> 36,000 per half year -> 300 slab -> 50 a month.
        assertAmount("50", ProfessionalTaxSlabs.monthly("KL", new BigDecimal("6000"), 11, null));
    }

    @Test
    void stateNamesResolveToTableCodes() {
        assertEquals("KA", ProfessionalTaxSlabs.codeForStateName("Karnataka"));
        assertEquals("TN", ProfessionalTaxSlabs.codeForStateName(" tamil nadu "));
        assertEquals("MH", ProfessionalTaxSlabs.codeForStateName("mh"));
        assertEquals("NONE", ProfessionalTaxSlabs.codeForStateName("Delhi"));
        assertNull(ProfessionalTaxSlabs.codeForStateName("Atlantis"));
    }
}
