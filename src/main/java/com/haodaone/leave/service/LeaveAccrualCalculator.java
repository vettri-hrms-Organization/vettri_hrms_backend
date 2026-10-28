package com.haodaone.leave.service;

import com.haodaone.leave.entity.LeaveType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pure, deterministic leave-policy arithmetic (no repositories, no clock): the accrual schedule for a
 * leave year, the balance cap, year-end carry-forward and comp-off FIFO/lapse. Everything a balance
 * shows "as of" a date is derived from these functions, so nothing depends on a job having run.
 *
 * <p>Leave year = calendar year (Jan-Dec). Rounding is applied to the cumulative accrual, never per
 * credit, so twelve monthly credits of 1.25 days always add up to exactly the annual entitlement.
 */
public final class LeaveAccrualCalculator {

    public static final String YEARLY_UPFRONT = "YEARLY_UPFRONT";
    public static final String MONTHLY = "MONTHLY";
    public static final String QUARTERLY = "QUARTERLY";
    public static final String ROUND_HALF_DAY = "HALF_DAY";
    public static final String ROUND_EXACT = "EXACT";

    private LeaveAccrualCalculator() {
    }

    /** The accrual-relevant subset of a LeaveType. */
    public record Policy(double annualDays, String mode, String rounding, boolean prorateOnJoining,
                         Integer joiningCutoffDay, Integer probationDays) {
        public static Policy of(LeaveType type) {
            return new Policy(type.getDefaultDaysPerYear(), type.getAccrualMode(), type.getAccrualRounding(),
                    type.isProrateOnJoining(), type.getJoiningCutoffDay(), type.getProbationDays());
        }
    }

    /** One accrual credit: days credited on date (already rounding-adjusted). */
    public record Credit(LocalDate date, double days) {
    }

    public static String normalizeMode(String mode) {
        String value = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case MONTHLY, QUARTERLY -> value;
            default -> YEARLY_UPFRONT;
        };
    }

    public static double round(double days, String rounding) {
        if (ROUND_EXACT.equalsIgnoreCase(rounding)) {
            return Math.round(days * 100.0) / 100.0;
        }
        return Math.round(days * 2.0) / 2.0;
    }

    /**
     * Credits earned in {@code year} on or before {@code asOf}.
     * <ul>
     *   <li>Eligibility starts at joining + probationDays (null joining = eligible all year).</li>
     *   <li>The eligibility month counts unless joiningCutoffDay is set and eligibility starts after that day.</li>
     *   <li>YEARLY_UPFRONT: whole entitlement on Jan 1 (or the eligibility date); prorated by remaining months only when prorateOnJoining.</li>
     *   <li>MONTHLY: annual/12 on the 1st of every eligible month (the first one on the eligibility date).</li>
     *   <li>QUARTERLY: annual/12 x eligible months of the quarter, on the 1st of Jan/Apr/Jul/Oct (or the eligibility date).</li>
     * </ul>
     */
    public static List<Credit> schedule(Policy policy, LocalDate joining, int year, LocalDate asOf) {
        List<Credit> credits = new ArrayList<>();
        if (policy == null || policy.annualDays() <= 0 || asOf == null) {
            return credits;
        }
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        int probation = policy.probationDays() == null ? 0 : Math.max(0, policy.probationDays());
        LocalDate eligibleFrom = joining == null ? null : joining.plusDays(probation);
        if (eligibleFrom != null && eligibleFrom.isAfter(yearEnd)) {
            return credits;
        }
        boolean startsThisYear = eligibleFrom != null && !eligibleFrom.isBefore(yearStart);
        int firstMonth = 1;
        if (startsThisYear) {
            firstMonth = eligibleFrom.getMonthValue();
            Integer cutoff = policy.joiningCutoffDay();
            if (cutoff != null && cutoff > 0 && eligibleFrom.getDayOfMonth() > cutoff) {
                firstMonth++;
            }
        }
        if (firstMonth > 12) {
            return credits;
        }
        double annual = policy.annualDays();
        double perMonth = annual / 12.0;
        String rounding = policy.rounding();

        switch (normalizeMode(policy.mode())) {
            case MONTHLY -> {
                double raw = 0;
                double rounded = 0;
                for (int month = firstMonth; month <= 12; month++) {
                    LocalDate date = creditDate(LocalDate.of(year, month, 1), startsThisYear ? eligibleFrom : null);
                    if (date.isAfter(asOf)) break;
                    raw += perMonth;
                    double next = round(raw, rounding);
                    credits.add(new Credit(date, next - rounded));
                    rounded = next;
                }
            }
            case QUARTERLY -> {
                double raw = 0;
                double rounded = 0;
                for (int quarterStart = 1; quarterStart <= 10; quarterStart += 3) {
                    int months = 0;
                    for (int month = quarterStart; month < quarterStart + 3; month++) {
                        if (month >= firstMonth) months++;
                    }
                    if (months == 0) continue;
                    LocalDate date = creditDate(LocalDate.of(year, quarterStart, 1), startsThisYear ? eligibleFrom : null);
                    if (date.isAfter(asOf)) break;
                    raw += perMonth * months;
                    double next = round(raw, rounding);
                    credits.add(new Credit(date, next - rounded));
                    rounded = next;
                }
            }
            default -> {
                LocalDate date = startsThisYear ? eligibleFrom : yearStart;
                if (date.isAfter(asOf)) break;
                double raw = (policy.prorateOnJoining() && startsThisYear) ? annual * (13 - firstMonth) / 12.0 : annual;
                credits.add(new Credit(date, round(raw, rounding)));
            }
        }
        return credits;
    }

    /** Total accrued in {@code year} as of {@code asOf} (sum of {@link #schedule}). */
    public static double allocatedAsOf(Policy policy, LocalDate joining, int year, LocalDate asOf) {
        double total = 0;
        for (Credit credit : schedule(policy, joining, year, asOf)) {
            total += credit.days();
        }
        return Math.round(total * 100.0) / 100.0;
    }

    private static LocalDate creditDate(LocalDate periodStart, LocalDate eligibleFrom) {
        return eligibleFrom != null && eligibleFrom.isAfter(periodStart) ? eligibleFrom : periodStart;
    }

    /**
     * Applies the max-balance cap: accrual stops once the balance would exceed {@code cap}.
     * otherCredits = carried + adjustments (never trimmed); debits = used + encashed + lapsed.
     */
    public static double capAllocation(double allocated, double otherCredits, double debits, Double cap) {
        if (cap == null || cap <= 0) {
            return allocated;
        }
        double room = cap + debits - otherCredits;
        return Math.max(0, Math.min(allocated, room));
    }

    /** Carried days that lapse at the carry-forward expiry: whatever was not used before it (carried days are used first). */
    public static double carryForwardLapsed(double carried, double usedBeforeExpiry, boolean expired) {
        if (!expired || carried <= 0) {
            return 0;
        }
        return Math.max(0, carried - Math.max(0, usedBeforeExpiry));
    }

    public record YearEndResult(double closing, double carried, double lapsed, double encashable) {
    }

    /**
     * Year-end split of a closing balance. Negative closings are not carried (nothing lapses either);
     * encashable is informational - the part of the lapsing days the type would allow to be encashed.
     */
    public static YearEndResult yearEnd(double closing, boolean carryForward, Double maxCarry, Double cap,
                                        boolean encashable, Double maxEncashablePerYear, double alreadyEncashed) {
        double positive = Math.max(0, closing);
        double carried = carryForward ? positive : 0;
        if (maxCarry != null && maxCarry >= 0) carried = Math.min(carried, maxCarry);
        if (cap != null && cap > 0) carried = Math.min(carried, cap);
        double lapsed = positive - carried;
        double encashableDays = 0;
        if (encashable) {
            encashableDays = maxEncashablePerYear == null ? lapsed : Math.min(lapsed, Math.max(0, maxEncashablePerYear - alreadyEncashed));
        }
        return new YearEndResult(closing, carried, lapsed, encashableDays);
    }

    public record CompOffCredit(Long id, LocalDate earnedOn, LocalDate expiresOn, double days) {
    }

    public record Debit(LocalDate date, double days) {
    }

    public record CompOffState(double credited, double consumed, double lapsed, double available,
                               List<CompOffLapse> lapses) {
    }

    public record CompOffLapse(Long creditId, LocalDate expiredOn, double days) {
    }

    /**
     * Comp-off credits are consumed earliest-expiry first; a credit only covers leave taken on or before its
     * expiry. Whatever is left of a credit after its expiry date (expiresOn &lt; asOf) has lapsed.
     */
    public static CompOffState compOffFifo(List<CompOffCredit> credits, List<Debit> debits, LocalDate asOf) {
        List<CompOffCredit> ordered = new ArrayList<>(credits);
        ordered.sort(Comparator.comparing(CompOffCredit::expiresOn, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CompOffCredit::earnedOn, Comparator.nullsLast(Comparator.naturalOrder())));
        double[] remaining = new double[ordered.size()];
        double credited = 0;
        for (int i = 0; i < ordered.size(); i++) {
            remaining[i] = ordered.get(i).days();
            credited += remaining[i];
        }
        List<Debit> orderedDebits = new ArrayList<>(debits);
        orderedDebits.sort(Comparator.comparing(Debit::date));
        double consumed = 0;
        for (Debit debit : orderedDebits) {
            consumed += debit.days();
            double need = debit.days();
            for (int i = 0; i < ordered.size() && need > 0; i++) {
                LocalDate expiry = ordered.get(i).expiresOn();
                if (remaining[i] <= 0 || (expiry != null && expiry.isBefore(debit.date()))) continue;
                double take = Math.min(remaining[i], need);
                remaining[i] -= take;
                need -= take;
            }
        }
        double lapsed = 0;
        List<CompOffLapse> lapses = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            LocalDate expiry = ordered.get(i).expiresOn();
            if (remaining[i] > 0 && expiry != null && expiry.isBefore(asOf)) {
                lapsed += remaining[i];
                lapses.add(new CompOffLapse(ordered.get(i).id(), expiry, remaining[i]));
            }
        }
        return new CompOffState(credited, consumed, lapsed, credited - consumed - lapsed, lapses);
    }
}
