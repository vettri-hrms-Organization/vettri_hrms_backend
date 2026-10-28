package com.haodaone.salary.accounting;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

/** Ledger (account) names used when exporting a payroll run to accounting. One row per company; defaults below. */
@Entity
@Table(name = "payroll_ledger_settings")
public class PayrollLedgerSettings extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(name = "salaries_ledger", nullable = false, length = 150)
    private String salariesLedger = "Salaries & Wages";

    @Column(name = "employer_pf_ledger", nullable = false, length = 150)
    private String employerPfLedger = "Employer PF Contribution";

    @Column(name = "employer_esi_ledger", nullable = false, length = 150)
    private String employerEsiLedger = "Employer ESI Contribution";

    @Column(name = "reimbursement_expense_ledger", nullable = false, length = 150)
    private String reimbursementExpenseLedger = "Employee Reimbursements";

    @Column(name = "salary_payable_ledger", nullable = false, length = 150)
    private String salaryPayableLedger = "Salary Payable";

    @Column(name = "pf_payable_ledger", nullable = false, length = 150)
    private String pfPayableLedger = "PF Payable";

    @Column(name = "esi_payable_ledger", nullable = false, length = 150)
    private String esiPayableLedger = "ESI Payable";

    @Column(name = "pt_payable_ledger", nullable = false, length = 150)
    private String ptPayableLedger = "Professional Tax Payable";

    @Column(name = "tds_payable_ledger", nullable = false, length = 150)
    private String tdsPayableLedger = "TDS Payable - Salaries";

    @Column(name = "reimbursement_payable_ledger", nullable = false, length = 150)
    private String reimbursementPayableLedger = "Reimbursements Payable";

    @Column(name = "other_deductions_ledger", nullable = false, length = 150)
    private String otherDeductionsLedger = "Other Salary Deductions";

    @Column(name = "voucher_type", nullable = false, length = 60)
    private String voucherType = "Journal";

    /** Exact company name as in Tally (SVCURRENTCOMPANY); blank = import into the company open in Tally. */
    @Column(name = "tally_company_name", length = 150)
    private String tallyCompanyName;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getSalariesLedger() { return salariesLedger; }
    public void setSalariesLedger(String v) { this.salariesLedger = v; }
    public String getEmployerPfLedger() { return employerPfLedger; }
    public void setEmployerPfLedger(String v) { this.employerPfLedger = v; }
    public String getEmployerEsiLedger() { return employerEsiLedger; }
    public void setEmployerEsiLedger(String v) { this.employerEsiLedger = v; }
    public String getReimbursementExpenseLedger() { return reimbursementExpenseLedger; }
    public void setReimbursementExpenseLedger(String v) { this.reimbursementExpenseLedger = v; }
    public String getSalaryPayableLedger() { return salaryPayableLedger; }
    public void setSalaryPayableLedger(String v) { this.salaryPayableLedger = v; }
    public String getPfPayableLedger() { return pfPayableLedger; }
    public void setPfPayableLedger(String v) { this.pfPayableLedger = v; }
    public String getEsiPayableLedger() { return esiPayableLedger; }
    public void setEsiPayableLedger(String v) { this.esiPayableLedger = v; }
    public String getPtPayableLedger() { return ptPayableLedger; }
    public void setPtPayableLedger(String v) { this.ptPayableLedger = v; }
    public String getTdsPayableLedger() { return tdsPayableLedger; }
    public void setTdsPayableLedger(String v) { this.tdsPayableLedger = v; }
    public String getReimbursementPayableLedger() { return reimbursementPayableLedger; }
    public void setReimbursementPayableLedger(String v) { this.reimbursementPayableLedger = v; }
    public String getOtherDeductionsLedger() { return otherDeductionsLedger; }
    public void setOtherDeductionsLedger(String v) { this.otherDeductionsLedger = v; }
    public String getVoucherType() { return voucherType; }
    public void setVoucherType(String v) { this.voucherType = v; }
    public String getTallyCompanyName() { return tallyCompanyName; }
    public void setTallyCompanyName(String v) { this.tallyCompanyName = v; }
}
