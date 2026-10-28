package com.haodaone.salary.accounting;

import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.PayrollItemAdjustment;
import com.haodaone.salary.entity.PayrollItemStatus;
import com.haodaone.salary.entity.SalaryComponents;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollAccountingExportTest {

    private static PayrollItem item(String basic, String hra, String pf, String esi, String pt, String tds, String other,
                                    String reimbursement, String erEpf, String erEps, String erEsi, String status) {
        SalaryComponents c = new SalaryComponents();
        c.setBasicSalary(new BigDecimal(basic));
        c.setHra(new BigDecimal(hra));
        c.setPf(new BigDecimal(pf));
        c.setEsi(new BigDecimal(esi));
        c.setProfessionalTax(new BigDecimal(pt));
        c.setTds(new BigDecimal(tds));
        c.setOtherDeductions(new BigDecimal(other));
        PayrollItem item = new PayrollItem();
        item.setComponents(c);
        item.setStatus(status);
        if (reimbursement != null) {
            PayrollItemAdjustment adj = new PayrollItemAdjustment();
            adj.setAmount(new BigDecimal(reimbursement));
            adj.setTaxable(false);
            item.addAdjustment(adj);
            PayrollItemAdjustment ot = new PayrollItemAdjustment();
            ot.setAmount(new BigDecimal("1000"));
            ot.setTaxable(true);
            item.addAdjustment(ot);
        }
        item.recalculate();
        item.setEmployerEpf(new BigDecimal(erEpf));
        item.setEmployerEps(new BigDecimal(erEps));
        item.setEmployerEsi(new BigDecimal(erEsi));
        return item;
    }

    private static PayrollJournal.Journal journal() {
        List<PayrollItem> items = List.of(
                item("30000", "15000", "1800", "0", "200", "2500", "500", "1200", "550", "1250", "0", PayrollItemStatus.PROCESSED),
                item("12000", "4000", "1440", "120", "0", "0", "0", null, "440", "1000", "520", PayrollItemStatus.PAID),
                item("50000", "0", "1800", "0", "200", "9000", "0", null, "550", "1250", "0", PayrollItemStatus.ON_HOLD));
        return PayrollJournal.build(items, new PayrollLedgerSettings(), LocalDate.of(2026, 10, 31), "PAYROLL-2026-10",
                "Salary for October 2026 & bonus <test>");
    }

    @Test
    void journalBalancesAndExcludesHeldItems() {
        PayrollJournal.Journal j = journal();
        assertThat(j.employees()).isEqualTo(2);
        assertThat(j.totalDebit()).isEqualByComparingTo(j.totalCredit());
        // gross (47,200 incl. 1,200 reimbursement + 1,000 OT, and 16,000) + employer (550+1250 + 440+1000+520)
        assertThat(j.totalDebit()).isEqualByComparingTo("66960.00");
        PayrollJournal.Entry salaries = j.entries().stream().filter(e -> e.ledger().equals("Salaries & Wages")).findFirst().orElseThrow();
        assertThat(salaries.debit()).isEqualByComparingTo("62000.00");
        PayrollJournal.Entry pfPayable = j.entries().stream().filter(e -> e.ledger().equals("PF Payable")).findFirst().orElseThrow();
        assertThat(pfPayable.credit()).isEqualByComparingTo("6480.00"); // 1800+1440 + 1800+1440 employer
        PayrollJournal.Entry reimb = j.entries().stream().filter(e -> e.ledger().equals("Reimbursements Payable")).findFirst().orElseThrow();
        assertThat(reimb.credit()).isEqualByComparingTo("1200.00");
    }

    @Test
    void tallyXmlIsWellFormedAndAmountsNetToZero() throws Exception {
        byte[] xml = AccountingExportFormats.tallyXml(journal(), "Journal", "Acme Pvt Ltd");
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        assertThat(doc.getDocumentElement().getTagName()).isEqualTo("ENVELOPE");
        assertThat(doc.getElementsByTagName("TALLYREQUEST").item(0).getTextContent()).isEqualTo("Import Data");
        assertThat(doc.getElementsByTagName("SVCURRENTCOMPANY").item(0).getTextContent()).isEqualTo("Acme Pvt Ltd");
        assertThat(doc.getElementsByTagName("DATE").item(0).getTextContent()).isEqualTo("20261031");
        assertThat(doc.getElementsByTagName("NARRATION").item(0).getTextContent()).contains("& bonus <test>");
        Element voucher = (Element) doc.getElementsByTagName("VOUCHER").item(0);
        assertThat(voucher.getAttribute("VCHTYPE")).isEqualTo("Journal");

        NodeList entries = doc.getElementsByTagName("ALLLEDGERENTRIES.LIST");
        assertThat(entries.getLength()).isGreaterThan(3);
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal debits = BigDecimal.ZERO;
        for (int i = 0; i < entries.getLength(); i++) {
            Element e = (Element) entries.item(i);
            BigDecimal amount = new BigDecimal(e.getElementsByTagName("AMOUNT").item(0).getTextContent());
            String deemedPositive = e.getElementsByTagName("ISDEEMEDPOSITIVE").item(0).getTextContent();
            // Tally convention: debit = ISDEEMEDPOSITIVE Yes with a negative amount.
            if ("Yes".equals(deemedPositive)) {
                assertThat(amount.signum()).isNegative();
                debits = debits.add(amount.negate());
            } else {
                assertThat(amount.signum()).isPositive();
            }
            sum = sum.add(amount);
        }
        assertThat(sum).isEqualByComparingTo("0");
        assertThat(debits).isEqualByComparingTo("66960.00");
    }

    @Test
    void csvExportsCarryEveryLedger() {
        PayrollJournal.Journal j = journal();
        String journalCsv = new String(AccountingExportFormats.journalCsv(j), StandardCharsets.UTF_8);
        assertThat(journalCsv).contains("Salaries & Wages,Dr,62000.00");
        assertThat(journalCsv).contains("Total,,66960.00,66960.00");
        String zoho = new String(AccountingExportFormats.zohoCsv(j), StandardCharsets.UTF_8);
        assertThat(zoho).contains("Journal Date,Reference Number,Notes,Journal Type,Currency,Account,Description,Debit,Credit");
        assertThat(zoho.lines().count()).isEqualTo(1 + j.entries().size());
        assertThat(AccountingExportFormats.csv("=SUM(A1)")).isEqualTo("'=SUM(A1)");
    }
}
