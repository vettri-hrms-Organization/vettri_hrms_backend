package com.haodaone.settlement.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.math.BigDecimal;

/** One earning or deduction line of a Full & Final settlement statement. */
@Entity
@Table(name = "final_settlement_line", indexes = {
        @Index(name = "idx_final_settlement_line_settlement", columnList = "settlement_id")
})
public class FinalSettlementLine extends BaseEntity {

    public static final String EARNING = "EARNING";
    public static final String DEDUCTION = "DEDUCTION";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private FinalSettlement settlement;

    /** EARNING or DEDUCTION. */
    @Column(nullable = false, length = 12)
    private String kind;

    /** SALARY, LEAVE_ENCASHMENT, GRATUITY, BONUS, OTHER_EARNING, OVERTIME, REIMBURSEMENT, PF, ESI, PT, TDS, NOTICE_RECOVERY, ADVANCE, ASSET_RECOVERY, OTHER_DEDUCTION... */
    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    /** Part of an EARNING that counts as taxable salary (after s.10 exemptions); 0 for deductions. */
    @Column(name = "taxable_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    /** Entered by HR (kept across recalculation) rather than computed. */
    @Column(nullable = false)
    private boolean manual = false;

    /** PayrollAdjustmentProvider source (EXPENSE_CLAIM, OVERTIME_ENTRY, LEAVE_ENCASHMENT) for lines settled on approval. */
    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public FinalSettlement getSettlement() { return settlement; }
    public void setSettlement(FinalSettlement settlement) { this.settlement = settlement; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount == null ? BigDecimal.ZERO : amount; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount == null ? BigDecimal.ZERO : taxableAmount; }
    public boolean isManual() { return manual; }
    public void setManual(boolean manual) { this.manual = manual; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isEarning() {
        return EARNING.equals(kind);
    }
}
