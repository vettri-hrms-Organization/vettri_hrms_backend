package com.haodaone.billing.gst;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BillingGstTest {

    @Test
    void gstinChecksumAcceptsValidAndRejectsTamperedNumbers() {
        assertThat(GstinValidator.isValid("27AAPFU0939F1ZV")).isTrue();
        assertThat(GstinValidator.isValid("29AAGCB7383J1Z4")).isTrue();
        assertThat(GstinValidator.isValid(" 29aagcb7383j1z4 ")).isTrue();
        assertThat(GstinValidator.isValid("27AAPFU0939F1ZA")).isFalse(); // wrong check digit
        assertThat(GstinValidator.isValid("27AAPFU0939F1Z")).isFalse();  // too short
        assertThat(GstinValidator.isValid("27AAPFU0939F1XV")).isFalse(); // 14th char must be Z
        assertThat(GstinValidator.isValid("")).isFalse();
        assertThat(GstinValidator.isValid(null)).isFalse();
        assertThat(GstinValidator.stateCodeOf("29AAGCB7383J1Z4")).isEqualTo("29");
    }

    @Test
    void intraStateSupplySplitsIntoCgstAndSgst() {
        GstBreakup gst = GstCalculator.compute(new BigDecimal("1000.00"), "33AAACH7409R1Z8", "33", "33", null);
        assertThat(gst.intraState()).isTrue();
        assertThat(gst.cgstAmount()).isEqualByComparingTo("90.00");
        assertThat(gst.sgstAmount()).isEqualByComparingTo("90.00");
        assertThat(gst.igstAmount()).isEqualByComparingTo("0");
        assertThat(gst.total()).isEqualByComparingTo("1180.00");
        assertThat(gst.placeOfSupplyCode()).isEqualTo("33");
    }

    @Test
    void interStateSupplyUsesIgstAndFallsBackToBuyerGstinState() {
        GstBreakup gst = GstCalculator.compute(new BigDecimal("4975.00"), "33AAACH7409R1Z8", null, null, "29AAGCB7383J1Z4");
        assertThat(gst.intraState()).isFalse();
        assertThat(gst.igstRate()).isEqualByComparingTo("18");
        assertThat(gst.igstAmount()).isEqualByComparingTo("895.50");
        assertThat(gst.cgstAmount()).isEqualByComparingTo("0");
        assertThat(gst.total()).isEqualByComparingTo("5870.50");
        assertThat(gst.placeOfSupplyCode()).isEqualTo("29");
    }

    @Test
    void noGstWhenSellerGstinIsNotConfigured() {
        GstBreakup gst = GstCalculator.compute(new BigDecimal("500.00"), "", "33", "29", null);
        assertThat(gst.taxApplied()).isFalse();
        assertThat(gst.totalTax()).isEqualByComparingTo("0");
        assertThat(gst.total()).isEqualByComparingTo("500.00");
        assertThat(gst.note()).contains("GSTIN not provided");
    }

    @Test
    void financialYearAndAmountInWords() {
        assertThat(FinancialYear.of(LocalDate.of(2026, 4, 1))).isEqualTo("2026-27");
        assertThat(FinancialYear.of(LocalDate.of(2027, 3, 31))).isEqualTo("2026-27");
        assertThat(FinancialYear.of(LocalDate.of(2099, 12, 1))).isEqualTo("2099-00");
        assertThat(AmountInWords.rupees(new BigDecimal("120050.50")))
                .isEqualTo("Rupees One Lakh Twenty Thousand Fifty and Fifty Paise Only");
        assertThat(AmountInWords.rupees(new BigDecimal("12500000"))).isEqualTo("Rupees One Crore Twenty Five Lakh Only");
        assertThat(AmountInWords.rupees(BigDecimal.ZERO)).isEqualTo("Rupees Zero Only");
    }
}
