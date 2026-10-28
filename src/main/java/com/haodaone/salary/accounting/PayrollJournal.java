package com.haodaone.salary.accounting;

import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.PayrollItemStatus;
import com.haodaone.salary.entity.SalaryComponents;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Summary journal for one payroll run. Pure (unit-tested).
 *
 * <pre>
 * Dr Salaries &amp; Wages            gross - non-taxable reimbursement lines
 * Dr Employer PF contribution      employer EPF + EPS
 * Dr Employer ESI contribution     employer ESI
 * Dr Employee reimbursements       reimbursement lines paid through payroll
 *    Cr Salary payable             net pay - reimbursements
 *    Cr PF payable                 employee PF + employer EPF + EPS
 *    Cr ESI payable                employee ESI + employer ESI
 *    Cr Professional tax payable   PT
 *    Cr TDS payable                TDS
 *    Cr Reimbursements payable     reimbursements (so salary and reimbursements can be cleared separately)
 *    Cr Other salary deductions    other deductions
 * </pre>
 * Debits = gross + employer contributions = credits. Lines mapped to the same ledger name are netted
 * into one entry; zero entries are dropped. ON_HOLD items are excluded.
 */
public final class PayrollJournal {

    public record Entry(String ledger, BigDecimal debit, BigDecimal credit) {
        public boolean isDebit() {
            return debit.signum() > 0;
        }
    }

    public record Journal(LocalDate date, String reference, String narration, List<Entry> entries, int employees) {
        public BigDecimal totalDebit() {
            return entries.stream().map(Entry::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal totalCredit() {
            return entries.stream().map(Entry::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private PayrollJournal() {
    }

    public static Journal build(List<PayrollItem> items, PayrollLedgerSettings ledgers, LocalDate date, String reference, String narration) {
        BigDecimal gross = BigDecimal.ZERO, reimb = BigDecimal.ZERO, net = BigDecimal.ZERO;
        BigDecimal erPf = BigDecimal.ZERO, erEsi = BigDecimal.ZERO;
        BigDecimal pf = BigDecimal.ZERO, esi = BigDecimal.ZERO, pt = BigDecimal.ZERO, tds = BigDecimal.ZERO, other = BigDecimal.ZERO;
        int count = 0;
        for (PayrollItem item : items) {
            if (item == null || item.isDeleted() || PayrollItemStatus.ON_HOLD.equals(item.getStatus())) continue;
            count++;
            SalaryComponents c = item.getComponents();
            gross = gross.add(nz(item.getGrossSalary()));
            net = net.add(nz(item.getNetSalary()));
            reimb = reimb.add(item.sumAdjustments(false).subtract(item.sumAdjustments(true)));
            erPf = erPf.add(nz(item.getEmployerEpf())).add(nz(item.getEmployerEps()));
            erEsi = erEsi.add(nz(item.getEmployerEsi()));
            pf = pf.add(nz(c.getPf()));
            esi = esi.add(nz(c.getEsi()));
            pt = pt.add(nz(c.getProfessionalTax()));
            tds = tds.add(nz(c.getTds()));
            other = other.add(nz(c.getOtherDeductions()));
        }
        PayrollLedgerSettings l = ledgers == null ? new PayrollLedgerSettings() : ledgers;
        // ledger -> signed amount (positive = debit)
        Map<String, BigDecimal> net2 = new LinkedHashMap<>();
        post(net2, l.getSalariesLedger(), gross.subtract(reimb));
        post(net2, l.getEmployerPfLedger(), erPf);
        post(net2, l.getEmployerEsiLedger(), erEsi);
        post(net2, l.getReimbursementExpenseLedger(), reimb);
        post(net2, l.getSalaryPayableLedger(), net.subtract(reimb).negate());
        post(net2, l.getPfPayableLedger(), pf.add(erPf).negate());
        post(net2, l.getEsiPayableLedger(), esi.add(erEsi).negate());
        post(net2, l.getPtPayableLedger(), pt.negate());
        post(net2, l.getTdsPayableLedger(), tds.negate());
        post(net2, l.getReimbursementPayableLedger(), reimb.negate());
        post(net2, l.getOtherDeductionsLedger(), other.negate());

        List<Entry> entries = new ArrayList<>();
        // Debits first, then credits (accountant-friendly order).
        net2.forEach((ledger, amount) -> {
            if (amount.signum() > 0) entries.add(new Entry(ledger, scale(amount), BigDecimal.ZERO.setScale(2)));
        });
        net2.forEach((ledger, amount) -> {
            if (amount.signum() < 0) entries.add(new Entry(ledger, BigDecimal.ZERO.setScale(2), scale(amount.negate())));
        });
        return new Journal(date, reference, narration, entries, count);
    }

    private static void post(Map<String, BigDecimal> map, String ledger, BigDecimal signed) {
        if (signed == null || signed.signum() == 0) return;
        String name = ledger == null || ledger.isBlank() ? "Unmapped" : ledger.trim();
        map.merge(name, signed, BigDecimal::add);
        if (map.get(name).signum() == 0) map.remove(name);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
