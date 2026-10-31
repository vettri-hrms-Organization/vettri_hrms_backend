package com.haodaone.salary.bank;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/** The company's salary debit account and preferred bank file format (V62). */
@Entity
@Table(name = "payroll_bank_settings")
public class PayrollBankSettings extends BaseEntity {

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(name = "debit_account_number", length = 34)
    private String debitAccountNumber;

    @Column(name = "debit_ifsc", length = 11)
    private String debitIfsc;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "default_format", nullable = false, length = 20)
    private String defaultFormat = "GENERIC";

    /** Narration on the transfer, e.g. "SALARY {MONTH}"; {MONTH} becomes "OCT 2026". */
    @Column(length = 30)
    private String narration;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getDebitAccountNumber() { return debitAccountNumber; }
    public void setDebitAccountNumber(String debitAccountNumber) { this.debitAccountNumber = debitAccountNumber; }
    public String getDebitIfsc() { return debitIfsc; }
    public void setDebitIfsc(String debitIfsc) { this.debitIfsc = debitIfsc; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getDefaultFormat() { return defaultFormat; }
    public void setDefaultFormat(String defaultFormat) { this.defaultFormat = defaultFormat; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
}
