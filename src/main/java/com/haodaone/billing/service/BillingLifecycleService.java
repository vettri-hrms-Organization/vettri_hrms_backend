package com.haodaone.billing.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.billing.entity.Invoice;
import com.haodaone.billing.entity.InvoiceStatus;
import com.haodaone.billing.entity.InvoiceType;
import com.haodaone.billing.entity.PaymentTransaction;
import com.haodaone.billing.entity.SeatAddOnCharge;
import com.haodaone.billing.gst.FinancialYear;
import com.haodaone.billing.gst.GstBreakup;
import com.haodaone.billing.gst.GstCalculator;
import com.haodaone.billing.gst.IndianStates;
import com.haodaone.billing.repository.InvoiceRepository;
import com.haodaone.billing.repository.SeatAddOnChargeRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.entity.Plan;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.entity.SubscriptionStatus;
import com.haodaone.company.repository.SubscriptionRepository;
import com.haodaone.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Subscription lifecycle state machine.
 *
 * <pre>
 *  TRIALING --(trial end, unpaid)--------------------------> EXPIRED (read-only)
 *  ACTIVE   --(T-7: renewal invoice PENDING + reminders T-7/T-3/T-1)
 *           --(renewal date reached, unpaid)---------------> PAST_DUE (grace window, full access)
 *  PAST_DUE --(grace end = renewal + app.billing.grace-days)--> SUSPENDED (read-only)
 *  ACTIVE   --(cancel at period end, renewal date reached)---> CANCELLED (read-only)
 *  any      --(renewal invoice paid)------------------------> ACTIVE, renewal extended by the cycle
 * </pre>
 *
 * The daily job is idempotent: invoices are only created when no PENDING one
 * exists, transitions are guarded by the current status, and reminders are
 * deduplicated by the notification key.
 */
@Service
public class BillingLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(BillingLifecycleService.class);
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final Set<SubscriptionStatus> READ_ONLY_STATUSES =
            EnumSet.of(SubscriptionStatus.SUSPENDED, SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED);
    /** Days before the renewal date the renewal invoice is generated (first reminder). */
    public static final int INVOICE_LEAD_DAYS = 7;
    /** Employee cap applied when a trial converts to a paid plan (matches BillingService plan specs). */
    public static final int PAID_PLAN_EMPLOYEE_LIMIT = 1000;
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.BASIC_ISO_DATE;

    private final SubscriptionRepository subscriptions;
    private final InvoiceRepository invoices;
    private final SeatAddOnChargeRepository addOns;
    private final SeatCountService seats;
    private final InvoiceNumberService numbers;
    private final BillingSettings settings;
    private final BillingNotifier notifier;
    private final AuditLogService audit;
    private final TransactionTemplate perSubscriptionTx;

    public BillingLifecycleService(SubscriptionRepository subscriptions, InvoiceRepository invoices,
                                   SeatAddOnChargeRepository addOns, SeatCountService seats,
                                   InvoiceNumberService numbers, BillingSettings settings,
                                   BillingNotifier notifier, AuditLogService audit,
                                   PlatformTransactionManager transactionManager) {
        this.subscriptions = subscriptions;
        this.invoices = invoices;
        this.addOns = addOns;
        this.seats = seats;
        this.numbers = numbers;
        this.settings = settings;
        this.notifier = notifier;
        this.audit = audit;
        if (transactionManager != null) {
            this.perSubscriptionTx = new TransactionTemplate(transactionManager);
            this.perSubscriptionTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        } else {
            this.perSubscriptionTx = null;
        }
    }

    public static LocalDate today() {
        return LocalDate.now(IST);
    }

    public static boolean isReadOnly(SubscriptionStatus status) {
        return status != null && READ_ONLY_STATUSES.contains(status);
    }

    public static boolean isTrial(SubscriptionStatus status) {
        return status == SubscriptionStatus.TRIALING || status == SubscriptionStatus.TRIAL;
    }

    public static int cycleMonths(String cycle) {
        if (cycle == null) return 1;
        return switch (cycle.trim().toUpperCase()) {
            case BillingPricing.QUARTERLY -> 3;
            case BillingPricing.ANNUAL -> 12;
            default -> 1;
        };
    }

    // ------------------------------------------------------------------ daily job

    /** Runs the lifecycle for every subscription, each in its own transaction. Returns the number processed. */
    public int runDaily(LocalDate today) {
        List<Long> ids = subscriptions.findAllByDeletedFalse().stream().map(Subscription::getId).toList();
        int processed = 0;
        for (Long id : ids) {
            try {
                if (perSubscriptionTx != null) {
                    perSubscriptionTx.executeWithoutResult(status -> processSubscription(id, today));
                } else {
                    processSubscription(id, today);
                }
                processed++;
            } catch (RuntimeException ex) {
                log.error("Billing lifecycle failed for subscription {}", id, ex);
            }
        }
        return processed;
    }

    @Transactional
    public void processSubscription(Long subscriptionId, LocalDate today) {
        subscriptions.findById(subscriptionId).filter(s -> !s.isDeleted()).ifPresent(sub -> evaluate(sub, today));
    }

    void evaluate(Subscription sub, LocalDate today) {
        if (sub.getStatus() == null || sub.getRenewalDate() == null || sub.getCompany() == null) return;
        // Sales demo workspaces are never invoiced, reminded or suspended.
        if (sub.getCompany().isDemo()) return;
        switch (sub.getStatus()) {
            case TRIAL, TRIALING -> handleTrial(sub, today);
            case ACTIVE -> handleActive(sub, today);
            case PAST_DUE -> handlePastDue(sub, today, ensureDueInvoiceIfMissing(sub, today));
            default -> { /* PENDING_PAYMENT, SUSPENDED, EXPIRED, CANCELLED: nothing until paid / changed by an admin */ }
        }
    }

    private void handleTrial(Subscription sub, LocalDate today) {
        long daysLeft = ChronoUnit.DAYS.between(today, sub.getRenewalDate());
        if (daysLeft == 7 || daysLeft == 3 || daysLeft == 1) {
            notifier.trialEnding(sub, daysLeft);
        }
        if (daysLeft <= 0) {
            Invoice due = ensureDueInvoice(sub, today);
            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setSuspendedAt(LocalDateTime.now(IST));
            sub.setSuspensionReason("TRIAL_ENDED");
            subscriptions.save(sub);
            auditFor(sub, "TRIAL_EXPIRED", "Trial ended unpaid on " + sub.getRenewalDate() + "; workspace is read-only.");
            notifier.trialEnded(sub, due);
        }
    }

    private void handleActive(Subscription sub, LocalDate today) {
        recordSeatAddOns(sub, today);
        LocalDate renewal = sub.getRenewalDate();
        if (sub.isCancelAtPeriodEnd() && !today.isBefore(renewal)) {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            sub.setSuspendedAt(LocalDateTime.now(IST));
            sub.setSuspensionReason("CANCELLED");
            subscriptions.save(sub);
            pendingInvoice(sub).ifPresent(invoice -> {
                if (invoice.getRazorpayOrderId() == null) {
                    invoice.setStatus(InvoiceStatus.CANCELLED);
                    invoices.save(invoice);
                }
            });
            auditFor(sub, "SUBSCRIPTION_CANCELLED", "Cancelled at period end " + renewal);
            notifier.cancelled(sub);
            return;
        }
        long daysLeft = ChronoUnit.DAYS.between(today, renewal);
        if (daysLeft > INVOICE_LEAD_DAYS || sub.isCancelAtPeriodEnd()) return;

        Invoice due = ensureDueInvoice(sub, today);
        if (daysLeft == 7 || daysLeft == 3 || daysLeft == 1) {
            notifier.renewalReminder(sub, due, daysLeft);
        }
        if (daysLeft <= 0) {
            sub.setStatus(SubscriptionStatus.PAST_DUE);
            sub.setPastDueSince(renewal);
            sub.setGraceEndsOn(renewal.plusDays(settings.graceDays()));
            subscriptions.save(sub);
            auditFor(sub, "SUBSCRIPTION_PAST_DUE", "Renewal " + renewal + " unpaid; grace until " + sub.getGraceEndsOn());
            notifier.pastDue(sub, due);
            handlePastDue(sub, today, due);
        }
    }

    private void handlePastDue(Subscription sub, LocalDate today, Invoice due) {
        LocalDate graceEnd = sub.getGraceEndsOn() != null ? sub.getGraceEndsOn() : sub.getRenewalDate().plusDays(settings.graceDays());
        if (sub.getGraceEndsOn() == null) {
            sub.setGraceEndsOn(graceEnd);
            subscriptions.save(sub);
        }
        if (ChronoUnit.DAYS.between(today, graceEnd) == 1) {
            notifier.suspensionWarning(sub, due);
        }
        if (today.isAfter(graceEnd)) {
            sub.setStatus(SubscriptionStatus.SUSPENDED);
            sub.setSuspendedAt(LocalDateTime.now(IST));
            sub.setSuspensionReason("NON_PAYMENT");
            subscriptions.save(sub);
            auditFor(sub, "SUBSCRIPTION_SUSPENDED", "Suspended for non-payment after grace ended " + graceEnd);
            notifier.suspended(sub, due);
        }
    }

    // ----------------------------------------------------------------- invoices

    public Optional<Invoice> pendingInvoice(Subscription sub) {
        if (sub == null || sub.getId() == null) return Optional.empty();
        return invoices.findFirstBySubscription_IdAndStatusAndInvoiceTypeAndDeletedFalseOrderByIdDesc(
                sub.getId(), InvoiceStatus.PENDING, InvoiceType.SUBSCRIPTION);
    }

    private Invoice ensureDueInvoiceIfMissing(Subscription sub, LocalDate today) {
        return pendingInvoice(sub).orElseGet(() -> ensureDueInvoice(sub, today));
    }

    /**
     * Returns the PENDING renewal invoice for the next period, creating it if
     * needed. While no Razorpay order is attached the amounts are refreshed
     * (current seats, plan/cycle changes, new seat add-ons).
     */
    @Transactional
    public Invoice ensureDueInvoice(Subscription sub, LocalDate today) {
        Invoice invoice = pendingInvoice(sub).orElse(null);
        if (invoice == null) {
            LocalDate start = sub.getRenewalDate() != null ? sub.getRenewalDate() : today;
            invoice = new Invoice();
            invoice.setInvoiceNumber("DUE-" + sub.getId() + "-" + COMPACT.format(start) + "-"
                    + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
            invoice.setCompany(sub.getCompany());
            invoice.setSubscription(sub);
            invoice.setInvoiceType(InvoiceType.SUBSCRIPTION);
            invoice.setStatus(InvoiceStatus.PENDING);
            invoice.setInvoiceDate(today);
            invoice.setDueDate(start.isBefore(today) ? today : start);
            invoice.setBillingPeriodStart(start);
            invoice.setCurrency("INR");
            invoice.setSubtotal(BigDecimal.ZERO);
            invoice.setTotal(BigDecimal.ZERO);
            invoice.setEmailStatus("NOT_SENT");
            invoice = invoices.save(invoice);
            refreshAmounts(invoice, sub);
            return invoices.save(invoice);
        }
        if (invoice.getRazorpayOrderId() == null) {
            refreshAmounts(invoice, sub);
            invoice = invoices.save(invoice);
        }
        return invoice;
    }

    /** Recomputes plan, seats, add-ons and GST of a PENDING invoice. */
    public void refreshAmounts(Invoice invoice, Subscription sub) {
        String plan = effectivePlan(sub);
        String cycle = effectiveCycle(sub);
        BigDecimal rate = BillingPricing.rateFor(plan, cycle);
        int seatCount = Math.max(1, seats.billableSeats(sub.getCompany().getId()));
        BigDecimal base = rate.multiply(BigDecimal.valueOf(seatCount)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal addOnTotal = BigDecimal.ZERO;
        if (invoice.getId() != null) {
            for (SeatAddOnCharge charge : addOns.findBySubscription_IdAndInvoiceIsNullAndDeletedFalseOrderByEffectiveDateAsc(sub.getId())) {
                charge.setInvoice(invoice);
                addOns.save(charge);
            }
            for (SeatAddOnCharge charge : addOns.findByInvoice_IdAndDeletedFalseOrderByEffectiveDateAsc(invoice.getId())) {
                addOnTotal = addOnTotal.add(charge.getAmount());
            }
        }
        BigDecimal subtotal = base.add(addOnTotal).setScale(2, RoundingMode.HALF_UP);

        invoice.setPlan(plan);
        invoice.setBillingCycle(cycle);
        invoice.setUnitRate(rate);
        invoice.setEmployeeCount(seatCount);
        invoice.setBaseAmount(base);
        invoice.setSeatAddonAmount(addOnTotal.setScale(2, RoundingMode.HALF_UP));
        invoice.setSubtotal(subtotal);
        invoice.setDiscount(BigDecimal.ZERO);
        LocalDate start = invoice.getBillingPeriodStart();
        if (start != null) {
            invoice.setBillingPeriodEnd(start.plusMonths(cycleMonths(cycle)).minusDays(1));
        }
        applyGst(invoice, sub.getCompany(), subtotal);
    }

    void applyGst(Invoice invoice, Company company, BigDecimal taxable) {
        GstBreakup gst = GstCalculator.compute(taxable, settings.sellerGstin(), settings.sellerStateCode(),
                company == null ? null : company.getBillingStateCode(), company == null ? null : company.getGstin());
        invoice.setTaxableValue(gst.taxableValue());
        invoice.setCgstRate(gst.cgstRate());
        invoice.setCgstAmount(gst.cgstAmount());
        invoice.setSgstRate(gst.sgstRate());
        invoice.setSgstAmount(gst.sgstAmount());
        invoice.setIgstRate(gst.igstRate());
        invoice.setIgstAmount(gst.igstAmount());
        invoice.setTax(gst.totalTax());
        invoice.setTotal(gst.total());
        invoice.setTaxNote(gst.note());
        invoice.setSacCode(settings.sac());
        invoice.setSellerGstin(settings.sellerGstin());
        invoice.setPlaceOfSupplyCode(gst.placeOfSupplyCode());
        invoice.setPlaceOfSupply(gst.placeOfSupplyCode() == null ? null : IndianStates.nameOf(gst.placeOfSupplyCode()));
        if (company != null) {
            invoice.setBuyerLegalName(company.getLegalName() == null || company.getLegalName().isBlank() ? company.getName() : company.getLegalName());
            invoice.setBuyerGstin(company.getGstin());
            invoice.setBuyerAddress(company.getBillingAddress());
            invoice.setBuyerPoNumber(company.getBillingPoNumber());
        }
    }

    public static String effectivePlan(Subscription sub) {
        if (sub.getPendingPlan() != null && !sub.getPendingPlan().isBlank()) return sub.getPendingPlan();
        if (sub.getPlan() == null || sub.getPlan() == Plan.TRIAL) return BillingPricing.STARTER;
        return sub.getPlan().name();
    }

    public static String effectiveCycle(Subscription sub) {
        if (sub.getPendingBillingCycle() != null && !sub.getPendingBillingCycle().isBlank()) return sub.getPendingBillingCycle();
        return sub.getBillingCycle() == null || sub.getBillingCycle().isBlank() ? BillingPricing.MONTHLY : sub.getBillingCycle();
    }

    // ------------------------------------------------------------------ payment

    /**
     * Marks a renewal invoice paid (idempotent): issues the gapless tax
     * invoice number, extends the subscription by the cycle and restores
     * ACTIVE. Returns false when the invoice was already paid.
     */
    @Transactional
    public boolean markInvoicePaid(Invoice invoice, PaymentTransaction payment, LocalDate today) {
        if (invoice.getStatus() == InvoiceStatus.PAID) return false;
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new BadRequestException("This invoice can no longer be paid. Please contact support.");
        }
        Subscription sub = invoice.getSubscription();
        boolean wasTrial = sub.getPlan() == Plan.TRIAL || isTrial(sub.getStatus()) || sub.getStatus() == SubscriptionStatus.EXPIRED;
        int months = cycleMonths(invoice.getBillingCycle());

        LocalDate start = invoice.getBillingPeriodStart() == null ? today : invoice.getBillingPeriodStart();
        if (isReadOnly(sub.getStatus()) && start.isBefore(today)) {
            start = today; // service was unavailable while suspended: the paid period starts on payment
        }
        invoice.setBillingPeriodStart(start);
        invoice.setBillingPeriodEnd(start.plusMonths(months).minusDays(1));

        invoice.setInvoiceNumber(numbers.nextInvoiceNumber(today));
        invoice.setFinancialYear(FinancialYear.of(today));
        invoice.setInvoiceDate(today);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now(IST));
        if (payment != null) {
            invoice.setPaymentTransaction(payment);
            invoice.setPaymentMethod(payment.getPaymentMethod());
            invoice.setRazorpayOrderId(payment.getRazorpayOrderId());
            invoice.setRazorpayPaymentId(payment.getRazorpayPaymentId());
        }
        invoices.save(invoice);

        sub.setPlan(Plan.valueOf(invoice.getPlan()));
        sub.setBillingCycle(invoice.getBillingCycle());
        sub.setRate(invoice.getUnitRate());
        sub.setBillableEmployeeCount(invoice.getEmployeeCount());
        sub.setPeriodPeakSeats(invoice.getEmployeeCount());
        sub.setAmount(invoice.getBaseAmount());
        sub.setStartDate(start);
        sub.setRenewalDate(start.plusMonths(months));
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setPastDueSince(null);
        sub.setGraceEndsOn(null);
        sub.setSuspendedAt(null);
        sub.setSuspensionReason(null);
        sub.setPendingPlan(null);
        sub.setPendingBillingCycle(null);
        sub.setCancelAtPeriodEnd(false);
        if (wasTrial && (sub.getEmployeeLimit() == null || sub.getEmployeeLimit() < PAID_PLAN_EMPLOYEE_LIMIT)) {
            sub.setEmployeeLimit(PAID_PLAN_EMPLOYEE_LIMIT);
        }
        subscriptions.save(sub);
        auditFor(sub, "SUBSCRIPTION_RENEWED", "Invoice " + invoice.getInvoiceNumber() + " paid (" + invoice.getTotal()
                + "); active until " + sub.getRenewalDate().minusDays(1));
        notifier.paymentReceived(sub, invoice);
        return true;
    }

    // ------------------------------------------------------------------- seats

    /**
     * Seats above the count already charged in the current paid period get a
     * prorated charge (rate x extra seats x remaining days / period days) that
     * is added to the next renewal invoice. Only applies once the company has
     * paid at least one subscription invoice (₹1 trial verifications excluded).
     */
    @Transactional
    public Optional<SeatAddOnCharge> recordSeatAddOns(Subscription sub, LocalDate today) {
        if (sub.getStatus() != SubscriptionStatus.ACTIVE || sub.getPlan() == Plan.TRIAL) return Optional.empty();
        if (sub.getRate() == null || sub.getRate().signum() <= 0 || sub.getStartDate() == null || sub.getRenewalDate() == null) return Optional.empty();
        if (!today.isBefore(sub.getRenewalDate())) return Optional.empty();
        if (!invoices.existsBySubscription_IdAndInvoiceTypeAndStatusAndDeletedFalse(sub.getId(), InvoiceType.SUBSCRIPTION, InvoiceStatus.PAID)) {
            return Optional.empty();
        }
        int current = seats.billableSeats(sub.getCompany().getId());
        int charged = sub.getPeriodPeakSeats() != null ? sub.getPeriodPeakSeats()
                : sub.getBillableEmployeeCount() != null ? sub.getBillableEmployeeCount() : current;
        if (sub.getPeriodPeakSeats() == null) {
            sub.setPeriodPeakSeats(charged);
            subscriptions.save(sub);
        }
        if (current <= charged) return Optional.empty();

        long periodDays = ChronoUnit.DAYS.between(sub.getStartDate(), sub.getRenewalDate());
        long remaining = ChronoUnit.DAYS.between(today, sub.getRenewalDate());
        if (periodDays <= 0 || remaining <= 0) return Optional.empty();
        int extra = current - charged;
        BigDecimal amount = proratedAmount(sub.getRate(), extra, remaining, periodDays);

        SeatAddOnCharge charge = new SeatAddOnCharge();
        charge.setCompany(sub.getCompany());
        charge.setSubscription(sub);
        charge.setSeats(extra);
        charge.setEffectiveDate(today);
        charge.setPeriodEnd(sub.getRenewalDate());
        charge.setDaysRemaining((int) remaining);
        charge.setPeriodDays((int) periodDays);
        charge.setUnitRate(sub.getRate());
        charge.setAmount(amount);
        SeatAddOnCharge saved = addOns.save(charge);
        sub.setPeriodPeakSeats(current);
        subscriptions.save(sub);
        auditFor(sub, "SEAT_ADDON", extra + " seat(s) added mid-cycle; prorated " + amount + " added to the next invoice");
        return Optional.of(saved);
    }

    public static BigDecimal proratedAmount(BigDecimal rate, int seats, long remainingDays, long periodDays) {
        return rate.multiply(BigDecimal.valueOf(seats)).multiply(BigDecimal.valueOf(remainingDays))
                .divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP);
    }

    // ----------------------------------------------------------- plan changes

    /**
     * Plan / cycle change requested by a company admin. During a trial (or
     * while unpaid) the choice applies to the next invoice. For a paid
     * subscription, a same-cycle switch to an equal or higher priced plan is
     * immediate (a higher price adds a prorated charge to the next invoice);
     * downgrades and cycle changes take effect at the next renewal.
     */
    @Transactional
    public String changePlan(Subscription sub, String requestedPlan, String requestedCycle, LocalDate today) {
        String plan = BillingPricing.normalizePlan(requestedPlan);
        if (plan == null || !BillingPricing.isExplicitPaidPlan(plan)) {
            throw new BadRequestException("Choose the Starter, Business or Enterprise plan.");
        }
        String cycle = BillingPricing.normalizeBillingCycle(requestedCycle);
        String message;
        boolean paidActive = sub.getStatus() == SubscriptionStatus.ACTIVE && sub.getPlan() != Plan.TRIAL
                && invoices.existsBySubscription_IdAndInvoiceTypeAndStatusAndDeletedFalse(sub.getId(), InvoiceType.SUBSCRIPTION, InvoiceStatus.PAID);
        if (!paidActive) {
            sub.setPendingPlan(plan);
            sub.setPendingBillingCycle(cycle);
            message = "Your next invoice will use the " + plan + " plan, billed " + cycle.toLowerCase() + ".";
        } else {
            String currentCycle = effectiveCurrentCycle(sub);
            BigDecimal currentRate = sub.getRate() != null ? sub.getRate() : BillingPricing.rateFor(sub.getPlan().name(), currentCycle);
            BigDecimal newRate = BillingPricing.rateFor(plan, cycle);
            boolean sameCycle = cycle.equals(currentCycle);
            if (sameCycle && newRate.compareTo(currentRate) >= 0) {
                if (newRate.compareTo(currentRate) > 0 && sub.getStartDate() != null && today.isBefore(sub.getRenewalDate())) {
                    long periodDays = ChronoUnit.DAYS.between(sub.getStartDate(), sub.getRenewalDate());
                    long remaining = ChronoUnit.DAYS.between(today, sub.getRenewalDate());
                    int paidSeats = sub.getPeriodPeakSeats() != null ? sub.getPeriodPeakSeats()
                            : sub.getBillableEmployeeCount() == null ? 1 : sub.getBillableEmployeeCount();
                    if (periodDays > 0 && remaining > 0) {
                        SeatAddOnCharge charge = new SeatAddOnCharge();
                        charge.setCompany(sub.getCompany());
                        charge.setSubscription(sub);
                        charge.setSeats(paidSeats);
                        charge.setEffectiveDate(today);
                        charge.setPeriodEnd(sub.getRenewalDate());
                        charge.setDaysRemaining((int) remaining);
                        charge.setPeriodDays((int) periodDays);
                        charge.setUnitRate(newRate.subtract(currentRate));
                        charge.setAmount(proratedAmount(newRate.subtract(currentRate), paidSeats, remaining, periodDays));
                        addOns.save(charge);
                    }
                }
                sub.setPlan(Plan.valueOf(plan));
                sub.setRate(newRate);
                sub.setPendingPlan(null);
                sub.setPendingBillingCycle(null);
                message = "You are now on the " + plan + " plan.";
            } else {
                sub.setPendingPlan(plan);
                sub.setPendingBillingCycle(cycle);
                message = "The change to " + plan + " (" + cycle.toLowerCase() + ") takes effect at your next renewal on "
                        + sub.getRenewalDate() + ".";
            }
        }
        subscriptions.save(sub);
        pendingInvoice(sub).filter(invoice -> invoice.getRazorpayOrderId() == null).ifPresent(invoice -> {
            refreshAmounts(invoice, sub);
            invoices.save(invoice);
        });
        auditFor(sub, "PLAN_CHANGE", "Requested " + plan + "/" + cycle + ": " + message);
        return message;
    }

    private static String effectiveCurrentCycle(Subscription sub) {
        return sub.getBillingCycle() == null || sub.getBillingCycle().isBlank() ? BillingPricing.MONTHLY : sub.getBillingCycle();
    }

    @Transactional
    public void setCancelAtPeriodEnd(Subscription sub, boolean cancel) {
        if (cancel && sub.getStatus() != SubscriptionStatus.ACTIVE && !isTrial(sub.getStatus())) {
            throw new BadRequestException("Only an active subscription can be cancelled.");
        }
        sub.setCancelAtPeriodEnd(cancel);
        subscriptions.save(sub);
        auditFor(sub, cancel ? "CANCEL_AT_PERIOD_END" : "CANCEL_REVOKED",
                cancel ? "Subscription will end on " + sub.getRenewalDate() : "Cancellation withdrawn");
    }

    // -------------------------------------------------------- platform admin

    @Transactional
    public Subscription extend(Subscription sub, int days, String reason) {
        if (days < 1 || days > 366) throw new BadRequestException("Extension must be between 1 and 366 days.");
        LocalDate today = today();
        LocalDate base = sub.getRenewalDate() == null || sub.getRenewalDate().isBefore(today) ? today : sub.getRenewalDate();
        sub.setRenewalDate(base.plusDays(days));
        if (sub.getStatus() == SubscriptionStatus.PAST_DUE || isReadOnly(sub.getStatus())) {
            sub.setStatus(isTrial(sub.getStatus()) || sub.getStatus() == SubscriptionStatus.EXPIRED ? SubscriptionStatus.TRIALING : SubscriptionStatus.ACTIVE);
            clearDunning(sub);
        }
        Subscription saved = subscriptions.save(sub);
        pendingInvoice(sub).filter(invoice -> invoice.getRazorpayOrderId() == null).ifPresent(invoice -> {
            invoice.setStatus(InvoiceStatus.CANCELLED);
            invoices.save(invoice);
        });
        auditFor(sub, "SUBSCRIPTION_EXTENDED", "Extended by " + days + " day(s) to " + sub.getRenewalDate() + ". Reason: " + reasonText(reason));
        return saved;
    }

    @Transactional
    public Subscription suspend(Subscription sub, String reason) {
        sub.setStatus(SubscriptionStatus.SUSPENDED);
        sub.setSuspendedAt(LocalDateTime.now(IST));
        sub.setSuspensionReason("ADMIN");
        Subscription saved = subscriptions.save(sub);
        auditFor(sub, "SUBSCRIPTION_SUSPENDED", "Suspended by platform admin. Reason: " + reasonText(reason));
        return saved;
    }

    /** Restores ACTIVE; when the renewal date has already passed it is moved 7 days out so the job does not re-suspend immediately. */
    @Transactional
    public Subscription reactivate(Subscription sub, String reason) {
        LocalDate today = today();
        sub.setStatus(sub.getPlan() == Plan.TRIAL ? SubscriptionStatus.TRIALING : SubscriptionStatus.ACTIVE);
        clearDunning(sub);
        if (sub.getRenewalDate() == null || !sub.getRenewalDate().isAfter(today)) {
            sub.setRenewalDate(today.plusDays(7));
        }
        Subscription saved = subscriptions.save(sub);
        auditFor(sub, "SUBSCRIPTION_REACTIVATED", "Reactivated by platform admin until " + sub.getRenewalDate() + ". Reason: " + reasonText(reason));
        return saved;
    }

    private static void clearDunning(Subscription sub) {
        sub.setPastDueSince(null);
        sub.setGraceEndsOn(null);
        sub.setSuspendedAt(null);
        sub.setSuspensionReason(null);
    }

    private static String reasonText(String reason) {
        return reason == null || reason.isBlank() ? "-" : reason.trim();
    }

    /** Audit rows are attributed to the subscription's company even when run by the scheduler or a platform admin. */
    void auditFor(Subscription sub, String action, String details) {
        if (audit == null) return;
        Long previous = TenantContext.getCurrentTenant();
        try {
            if (sub.getCompany() != null) TenantContext.setCurrentTenant(sub.getCompany().getId());
            audit.log("Subscription", sub.getId(), action, details);
        } catch (RuntimeException ex) {
            log.warn("Could not audit {} for subscription {}", action, sub.getId(), ex);
        } finally {
            if (previous != null) TenantContext.setCurrentTenant(previous); else TenantContext.clear();
        }
    }
}
