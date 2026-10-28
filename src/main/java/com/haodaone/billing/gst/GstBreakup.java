package com.haodaone.billing.gst;

import java.math.BigDecimal;

/**
 * Result of a GST computation. When the seller GSTIN is not configured no tax
 * is charged (all rates/amounts zero) and {@code note} explains why.
 */
public record GstBreakup(
        BigDecimal taxableValue,
        BigDecimal cgstRate, BigDecimal cgstAmount,
        BigDecimal sgstRate, BigDecimal sgstAmount,
        BigDecimal igstRate, BigDecimal igstAmount,
        BigDecimal totalTax,
        BigDecimal total,
        boolean intraState,
        boolean taxApplied,
        String placeOfSupplyCode,
        String note
) {
}
