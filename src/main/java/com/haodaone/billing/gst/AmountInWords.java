package com.haodaone.billing.gst;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Indian-numbering amount in words, e.g. "Rupees One Lakh Twenty Thousand and Fifty Paise Only". */
public final class AmountInWords {

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    private AmountInWords() {
    }

    public static String rupees(BigDecimal amount) {
        BigDecimal value = (amount == null ? BigDecimal.ZERO : amount).abs().setScale(2, RoundingMode.HALF_UP);
        long rupees = value.longValue();
        int paise = value.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        StringBuilder out = new StringBuilder("Rupees ");
        out.append(rupees == 0 ? "Zero" : words(rupees));
        if (paise > 0) {
            out.append(" and ").append(words(paise)).append(" Paise");
        }
        return out.append(" Only").toString();
    }

    static String words(long number) {
        if (number == 0) return "";
        StringBuilder sb = new StringBuilder();
        long crore = number / 10_000_000L;
        long rest = number % 10_000_000L;
        long lakh = rest / 100_000L;
        rest %= 100_000L;
        long thousand = rest / 1000L;
        rest %= 1000L;
        long hundred = rest / 100L;
        long units = rest % 100L;
        if (crore > 0) append(sb, words(crore) + " Crore");
        if (lakh > 0) append(sb, twoDigits((int) lakh) + " Lakh");
        if (thousand > 0) append(sb, twoDigits((int) thousand) + " Thousand");
        if (hundred > 0) append(sb, ONES[(int) hundred] + " Hundred");
        if (units > 0) append(sb, twoDigits((int) units));
        return sb.toString();
    }

    private static String twoDigits(int n) {
        if (n < 20) return ONES[n];
        return TENS[n / 10] + (n % 10 == 0 ? "" : " " + ONES[n % 10]);
    }

    private static void append(StringBuilder sb, String part) {
        if (sb.length() > 0) sb.append(' ');
        sb.append(part);
    }
}
