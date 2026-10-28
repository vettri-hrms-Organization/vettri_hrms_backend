package com.haodaone.expense.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expense_claim_line")
public class ExpenseClaimLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private ExpenseClaim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ExpenseCategory category;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "mileage_km", precision = 10, scale = 2)
    private BigDecimal mileageKm;

    @Column(name = "mileage_rate", precision = 8, scale = 2)
    private BigDecimal mileageRate;

    @Column(name = "receipt_s3_key", length = 500)
    private String receiptS3Key;

    @Column(name = "receipt_file_name", length = 255)
    private String receiptFileName;

    @Column(name = "receipt_content_type", length = 120)
    private String receiptContentType;

    @Column(name = "receipt_size_bytes")
    private Long receiptSizeBytes;

    public boolean hasReceipt() {
        return receiptS3Key != null && !receiptS3Key.isBlank();
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public ExpenseClaim getClaim() { return claim; }
    public void setClaim(ExpenseClaim claim) { this.claim = claim; }
    public ExpenseCategory getCategory() { return category; }
    public void setCategory(ExpenseCategory category) { this.category = category; }
    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getMileageKm() { return mileageKm; }
    public void setMileageKm(BigDecimal mileageKm) { this.mileageKm = mileageKm; }
    public BigDecimal getMileageRate() { return mileageRate; }
    public void setMileageRate(BigDecimal mileageRate) { this.mileageRate = mileageRate; }
    public String getReceiptS3Key() { return receiptS3Key; }
    public void setReceiptS3Key(String receiptS3Key) { this.receiptS3Key = receiptS3Key; }
    public String getReceiptFileName() { return receiptFileName; }
    public void setReceiptFileName(String receiptFileName) { this.receiptFileName = receiptFileName; }
    public String getReceiptContentType() { return receiptContentType; }
    public void setReceiptContentType(String receiptContentType) { this.receiptContentType = receiptContentType; }
    public Long getReceiptSizeBytes() { return receiptSizeBytes; }
    public void setReceiptSizeBytes(Long receiptSizeBytes) { this.receiptSizeBytes = receiptSizeBytes; }
}
