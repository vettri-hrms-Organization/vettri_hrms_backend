package com.haodaone.billing.entity;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/**
 * Gapless invoice-number counter, one row per series ("INV:2026-27",
 * "CN:2026-27"). Always read with a pessimistic write lock inside the
 * transaction that issues the document (see InvoiceNumberService).
 */
@Entity
@Table(name = "billing_invoice_counter", uniqueConstraints =
        @UniqueConstraint(name = "uk_billing_invoice_counter_series", columnNames = "series"))
public class InvoiceCounter extends BaseEntity {

    @Column(name = "series", nullable = false, length = 40)
    private String series;

    @Column(name = "last_value", nullable = false)
    private long lastValue;

    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }
    public long getLastValue() { return lastValue; }
    public void setLastValue(long lastValue) { this.lastValue = lastValue; }
}
