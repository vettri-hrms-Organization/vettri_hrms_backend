package com.haodaone.billing.gst;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 18% GST on SaaS subscriptions:
 *  - buyer state == seller state  -> CGST 9% + SGST 9% (intra-state)
 *  - otherwise                     -> IGST 18% (inter-state)
 *  - seller GSTIN not configured   -> no GST lines, a note instead.
 * Place of supply = buyer billing state code, else the state code in the
 * buyer GSTIN; if neither is known the supply is treated as intra-state
 * (place of supply = supplier location, the rule for unregistered
 * recipients whose address is not on record).
 */
public final class GstCalculator {

    public static final BigDecimal GST_RATE = new BigDecimal("18.00");
    public static final BigDecimal HALF_RATE = new BigDecimal("9.00");
    public static final String NO_GSTIN_NOTE = "GST not charged: supplier GSTIN not provided.";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private GstCalculator() {
    }

    public static GstBreakup compute(BigDecimal taxableValue, String sellerGstin, String sellerStateCode,
                                     String buyerStateCode, String buyerGstin) {
        BigDecimal taxable = (taxableValue == null ? BigDecimal.ZERO : taxableValue).setScale(2, RoundingMode.HALF_UP);
        String seller = IndianStates.normalizeCode(blankToNull(sellerStateCode));
        if (seller == null) seller = GstinValidator.stateCodeOf(blankToNull(sellerGstin));
        String buyer = IndianStates.normalizeCode(blankToNull(buyerStateCode));
        if (buyer == null) buyer = GstinValidator.stateCodeOf(blankToNull(buyerGstin));
        String placeOfSupply = buyer != null ? buyer : seller;

        if (blankToNull(sellerGstin) == null) {
            return new GstBreakup(taxable, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, taxable,
                    true, false, placeOfSupply, NO_GSTIN_NOTE);
        }

        boolean intra = seller == null || placeOfSupply == null || seller.equals(placeOfSupply);
        if (intra) {
            BigDecimal half = percent(taxable, HALF_RATE);
            BigDecimal tax = half.add(half);
            return new GstBreakup(taxable, HALF_RATE, half, HALF_RATE, half, ZERO, ZERO, tax, taxable.add(tax),
                    true, true, placeOfSupply, null);
        }
        BigDecimal igst = percent(taxable, GST_RATE);
        return new GstBreakup(taxable, ZERO, ZERO, ZERO, ZERO, GST_RATE, igst, igst, taxable.add(igst),
                false, true, placeOfSupply, null);
    }

    private static BigDecimal percent(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
