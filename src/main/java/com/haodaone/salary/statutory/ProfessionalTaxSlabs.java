package com.haodaone.salary.statutory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in professional tax slab tables, one row per state, as a plain data
 * table. Amounts are rupees per month on that month's gross salary, unless
 * the state is {@link Basis#HALF_YEARLY}.
 *
 * Half-yearly states (Tamil Nadu, Kerala) levy PT per half year (Apr-Sep,
 * Oct-Mar) on the half year's salary. We approximate the half-yearly income
 * as monthly gross x 6, look the slab up, and spread the half-yearly amount
 * over the six months: each month deducts round(amount / 6) and the last
 * month of the half (September / March) deducts the remainder, so a
 * full half year always adds up to exactly the statutory amount. An employee
 * whose pay changes mid-half-year may end up a few rupees off the slab that
 * applies to the actual half-year total - HR can correct with the
 * per-company fixed override or the "other deductions" line.
 *
 * Rates are as notified up to the effective-from noted per state. They
 * change rarely; edit the table (and its note) when a state amends them.
 * States that do not levy PT map to the NONE table (always zero).
 */
public final class ProfessionalTaxSlabs {

    private ProfessionalTaxSlabs() {
    }

    public enum Basis { MONTHLY, HALF_YEARLY }

    /** Income up to {@code upTo} (inclusive; null = no upper bound) pays {@code amount}. */
    public record Slab(BigDecimal upTo, BigDecimal amount) {
    }

    /**
     * @param februaryAmount if set, replaces the top slab's amount in February (MH/KA collect the annual
     *                       Rs 2,500 as 11 x 200 + 300)
     * @param femaleSlabs    optional slabs for women (Maharashtra); null = same as {@code slabs}
     */
    public record StateTable(String code, String name, Basis basis, List<Slab> slabs, BigDecimal februaryAmount,
                             List<Slab> femaleSlabs, String effectiveFrom, String notes) {
    }

    private static Slab s(Long upTo, long amount) {
        return new Slab(upTo == null ? null : BigDecimal.valueOf(upTo), BigDecimal.valueOf(amount));
    }

    private static final List<Slab> NIL = List.of(s(null, 0));

    private static final Map<String, StateTable> TABLES = new LinkedHashMap<>();

    static {
        add(new StateTable("KA", "Karnataka", Basis.MONTHLY,
                List.of(s(24_999L, 0), s(null, 200)), BigDecimal.valueOf(300), null,
                "2025-04-01", "Karnataka Tax on Professions (Amendment) Act 2025: Rs 200/month from Rs 25,000 gross, Rs 300 in February (annual Rs 2,500)."));
        add(new StateTable("MH", "Maharashtra", Basis.MONTHLY,
                List.of(s(7_500L, 0), s(10_000L, 175), s(null, 200)), BigDecimal.valueOf(300),
                List.of(s(25_000L, 0), s(null, 200)),
                "2023-07-01", "Men: nil up to 7,500, 175 up to 10,000, 200 above (300 in February). Women: nil up to 25,000, 200 above (300 in February)."));
        add(new StateTable("TN", "Tamil Nadu", Basis.HALF_YEARLY,
                List.of(s(21_000L, 0), s(30_000L, 180), s(45_000L, 425), s(60_000L, 930), s(75_000L, 1_025), s(null, 1_250)),
                null, null,
                "2024-04-01", "Half-yearly on half-year salary (Chennai Corporation revision 2024). Spread monthly; see class doc."));
        add(new StateTable("TG", "Telangana", Basis.MONTHLY,
                List.of(s(15_000L, 0), s(20_000L, 150), s(null, 200)), null, null,
                "2013-04-01", "Nil up to 15,000; 150 up to 20,000; 200 above."));
        add(new StateTable("AP", "Andhra Pradesh", Basis.MONTHLY,
                List.of(s(15_000L, 0), s(20_000L, 150), s(null, 200)), null, null,
                "2013-04-01", "Nil up to 15,000; 150 up to 20,000; 200 above."));
        add(new StateTable("WB", "West Bengal", Basis.MONTHLY,
                List.of(s(10_000L, 0), s(15_000L, 110), s(25_000L, 130), s(40_000L, 150), s(null, 200)), null, null,
                "2015-04-01", "Nil up to 10,000; 110 / 130 / 150 up to 15k / 25k / 40k; 200 above."));
        add(new StateTable("GJ", "Gujarat", Basis.MONTHLY,
                List.of(s(12_000L, 0), s(null, 200)), null, null,
                "2023-04-01", "Nil up to 12,000; 200 above."));
        add(new StateTable("KL", "Kerala", Basis.HALF_YEARLY,
                List.of(s(11_999L, 0), s(17_999L, 120), s(29_999L, 180), s(44_999L, 300), s(59_999L, 450),
                        s(74_999L, 600), s(99_999L, 750), s(124_999L, 1_000), s(null, 1_250)),
                null, null,
                "2023-04-01", "Half-yearly on half-year salary (Kerala Panchayat Raj / Municipality rules). Spread monthly; see class doc."));
        add(new StateTable("NONE", "No professional tax", Basis.MONTHLY, NIL, null, null, "-",
                "States/UTs that do not levy PT, e.g. Delhi, Haryana, Uttar Pradesh, Rajasthan, Uttarakhand, Himachal Pradesh, Chandigarh."));
    }

    private static void add(StateTable table) {
        TABLES.put(table.code(), table);
    }

    /** State/UT names (lower-case) that levy no PT and should resolve to NONE. */
    private static final List<String> NO_PT_STATES = List.of("delhi", "new delhi", "haryana", "uttar pradesh",
            "rajasthan", "uttarakhand", "himachal pradesh", "chandigarh", "ladakh", "andaman and nicobar islands",
            "dadra and nagar haveli and daman and diu", "lakshadweep", "arunachal pradesh");

    public static List<StateTable> all() {
        return List.copyOf(TABLES.values());
    }

    public static StateTable table(String code) {
        if (code == null) return null;
        return TABLES.get(code.trim().toUpperCase(Locale.ROOT));
    }

    /** Maps an office/state name ("Karnataka", "TAMIL NADU", "KA") to a table code, or null if unknown. */
    public static String codeForStateName(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (TABLES.containsKey(n.toUpperCase(Locale.ROOT))) return n.toUpperCase(Locale.ROOT);
        for (StateTable table : TABLES.values()) {
            if (table.name().equalsIgnoreCase(n)) return table.code();
        }
        if (n.equals("bangalore") || n.equals("bengaluru")) return "KA";
        if (NO_PT_STATES.contains(n)) return "NONE";
        return null;
    }

    /**
     * Monthly PT for an employee.
     *
     * @param monthlyGross the month's actual gross earnings
     * @param month        1-12, for February top-ups and the half-yearly remainder month
     * @param gender       employee gender, only used where the state has separate slabs for women
     */
    public static BigDecimal monthly(String stateCode, BigDecimal monthlyGross, int month, String gender) {
        StateTable table = table(stateCode);
        if (table == null || monthlyGross == null || monthlyGross.signum() <= 0) return BigDecimal.ZERO;
        boolean female = gender != null && (gender.equalsIgnoreCase("FEMALE") || gender.equalsIgnoreCase("F"));
        List<Slab> slabs = female && table.femaleSlabs() != null ? table.femaleSlabs() : table.slabs();

        if (table.basis() == Basis.HALF_YEARLY) {
            BigDecimal halfYear = lookup(slabs, monthlyGross.multiply(BigDecimal.valueOf(6)));
            BigDecimal perMonth = halfYear.divide(BigDecimal.valueOf(6), 0, RoundingMode.HALF_UP);
            if (month == 9 || month == 3) {
                return halfYear.subtract(perMonth.multiply(BigDecimal.valueOf(5))).max(BigDecimal.ZERO);
            }
            return perMonth;
        }

        int index = indexOf(slabs, monthlyGross);
        BigDecimal amount = slabs.get(index).amount();
        if (month == 2 && table.februaryAmount() != null && index == slabs.size() - 1 && amount.signum() > 0) {
            return table.februaryAmount();
        }
        return amount;
    }

    private static BigDecimal lookup(List<Slab> slabs, BigDecimal income) {
        return slabs.get(indexOf(slabs, income)).amount();
    }

    private static int indexOf(List<Slab> slabs, BigDecimal income) {
        for (int i = 0; i < slabs.size(); i++) {
            Slab slab = slabs.get(i);
            if (slab.upTo() == null || income.compareTo(slab.upTo()) <= 0) return i;
        }
        return slabs.size() - 1;
    }
}
