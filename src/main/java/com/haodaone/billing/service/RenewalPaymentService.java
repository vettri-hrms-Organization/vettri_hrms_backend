package com.haodaone.billing.service;

import com.haodaone.billing.entity.Invoice;
import com.haodaone.billing.entity.InvoiceStatus;
import com.haodaone.billing.entity.PaymentPurpose;
import com.haodaone.billing.entity.PaymentTransaction;
import com.haodaone.billing.entity.PaymentTransactionStatus;
import com.haodaone.billing.gst.GstCalculator;
import com.haodaone.billing.repository.InvoiceRepository;
import com.haodaone.billing.repository.PaymentTransactionRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.entity.SubscriptionStatus;
import com.haodaone.company.repository.SubscriptionRepository;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Paying renewal invoices.
 *
 * <p>Razorpay Orders ("Pay now"): a COMPANY_ADMIN creates an order for the
 * PENDING renewal invoice (seats x per-cycle rate + prorated add-ons + GST),
 * completes checkout and the authenticated verify step checks the checkout
 * signature, that the order belongs to the caller's company and invoice, and
 * that Razorpay reports the payment non-failed for exactly the invoice amount.
 * The payment.captured webhook runs the same idempotent completion.
 *
 * <p>Razorpay Subscriptions (auto-debit) are behind
 * app.billing.razorpay-subscriptions-enabled (default false).
 */
@Service
public class RenewalPaymentService {

    private static final Logger log = LoggerFactory.getLogger(RenewalPaymentService.class);

    private final SubscriptionRepository subscriptions;
    private final InvoiceRepository invoices;
    private final PaymentTransactionRepository payments;
    private final UserRepository users;
    private final BillingLifecycleService lifecycle;
    private final RazorpayGateway gateway;
    private final BillingNotifier notifier;
    private final BillingSettings settings;
    private final InvoiceService invoiceService;

    public RenewalPaymentService(SubscriptionRepository subscriptions, InvoiceRepository invoices,
                                 PaymentTransactionRepository payments, UserRepository users,
                                 BillingLifecycleService lifecycle, RazorpayGateway gateway,
                                 BillingNotifier notifier, BillingSettings settings, InvoiceService invoiceService) {
        this.subscriptions = subscriptions;
        this.invoices = invoices;
        this.payments = payments;
        this.users = users;
        this.lifecycle = lifecycle;
        this.gateway = gateway;
        this.notifier = notifier;
        this.settings = settings;
        this.invoiceService = invoiceService;
    }

    // ------------------------------------------------------------- Pay now

    @Transactional
    public Map<String, Object> createRenewalOrder(Long companyId, Long userId) {
        Subscription sub = subscription(companyId);
        User user = users.findById(userId).orElseThrow(() -> new BadRequestException("User not found."));
        LocalDate today = BillingLifecycleService.today();
        if (sub.getStatus() == SubscriptionStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Complete the signup checkout before paying a renewal.");
        }
        boolean hasPending = lifecycle.pendingInvoice(sub).isPresent();
        if (!hasPending && sub.getStatus() == SubscriptionStatus.ACTIVE && sub.getRenewalDate() != null
                && ChronoUnit.DAYS.between(today, sub.getRenewalDate()) > BillingLifecycleService.INVOICE_LEAD_DAYS) {
            throw new BadRequestException("Nothing is due yet. Your renewal invoice is generated 7 days before "
                    + sub.getRenewalDate() + ".");
        }
        if (!gateway.isConfigured()) {
            throw new BadRequestException("Payment is temporarily unavailable. Add Razorpay credentials to the backend environment.");
        }

        Invoice invoice = lifecycle.ensureDueInvoice(sub, today);
        BigDecimal previousTotal = invoice.getTotal();
        String previousOrder = invoice.getRazorpayOrderId();
        lifecycle.refreshAmounts(invoice, sub);
        if (invoice.getTotal() == null || invoice.getTotal().signum() <= 0) {
            throw new BadRequestException("This invoice has no amount due.");
        }
        long amountPaise = toPaise(invoice.getTotal());

        String orderId;
        boolean reuse = previousOrder != null && previousTotal != null && previousTotal.compareTo(invoice.getTotal()) == 0
                && payments.findByRazorpayOrderId(previousOrder)
                    .map(tx -> tx.getStatus() == PaymentTransactionStatus.CREATED).orElse(false);
        if (reuse) {
            orderId = previousOrder;
        } else {
            Map<String, String> notes = new LinkedHashMap<>();
            notes.put("companyId", String.valueOf(companyId));
            notes.put("userId", String.valueOf(userId));
            notes.put("invoiceId", String.valueOf(invoice.getId()));
            notes.put("purpose", PaymentPurpose.RENEWAL.name());
            notes.put("plan", invoice.getPlan());
            notes.put("billingCycle", invoice.getBillingCycle());
            orderId = gateway.createOrder(amountPaise, "vettri-renew-" + invoice.getId() + "-" + System.currentTimeMillis(), notes);

            PaymentTransaction tx = new PaymentTransaction();
            tx.setCompany(sub.getCompany());
            tx.setSubscription(sub);
            tx.setPlan(invoice.getPlan());
            tx.setRazorpayOrderId(orderId);
            tx.setCurrency(gateway.currency());
            tx.setAmount(invoice.getTotal());
            tx.setStatus(PaymentTransactionStatus.CREATED);
            tx.setPurpose(PaymentPurpose.RENEWAL);
            tx.setInvoiceId(invoice.getId());
            payments.save(tx);
            invoice.setRazorpayOrderId(orderId);
        }
        invoices.save(invoice);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("key", gateway.keyId());
        response.put("orderId", orderId);
        response.put("amount", amountPaise);
        response.put("currency", gateway.currency());
        response.put("invoiceId", invoice.getId());
        response.put("total", invoice.getTotal());
        response.put("description", invoice.getPlan() + " plan - " + invoice.getEmployeeCount() + " seat(s), "
                + invoice.getBillingCycle().toLowerCase());
        response.put("organizationName", sub.getCompany().getName());
        response.put("customerName", user.getFullName());
        response.put("customerEmail", user.getEmail());
        return response;
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public Map<String, Object> verifyRenewalPayment(Long companyId, Long userId, String orderId, String paymentId, String signature) {
        if (orderId == null || orderId.isBlank() || paymentId == null || paymentId.isBlank()) {
            throw new BadRequestException("Payment verification failed. Missing Razorpay data.");
        }
        if (!gateway.verifyPaymentSignature(orderId, paymentId, signature)) {
            throw new BadRequestException("Payment verification failed. Signature mismatch.");
        }
        PaymentTransaction tx = payments.findByCompany_IdAndRazorpayOrderId(companyId, orderId)
                .orElseThrow(() -> new BadRequestException("Payment order does not belong to the current company."));
        if (tx.getPurpose() != PaymentPurpose.RENEWAL || tx.getInvoiceId() == null) {
            throw new BadRequestException("This payment is not a subscription renewal.");
        }
        Invoice invoice = invoices.findByIdAndCompany_IdAndDeletedFalse(tx.getInvoiceId(), companyId)
                .orElseThrow(() -> new BadRequestException("Invoice not found."));

        if (tx.getStatus() == PaymentTransactionStatus.VERIFIED && invoice.getStatus() == InvoiceStatus.PAID) {
            return verifiedResponse(invoice, true);
        }

        JSONObject payment = gateway.fetchPayment(paymentId);
        String paymentOrder = payment.optString("order_id", "");
        String status = payment.optString("status", "");
        long paidPaise = payment.optLong("amount", -1L);
        if (!orderId.equals(paymentOrder)) {
            throw new BadRequestException("Payment does not belong to this order.");
        }
        if ("failed".equalsIgnoreCase(status)) {
            tx.setStatus(PaymentTransactionStatus.FAILED);
            tx.setRazorpayPaymentId(paymentId);
            payments.save(tx);
            notifier.paymentFailed(invoice.getSubscription(), tx.getId());
            throw new BadRequestException("Payment failed in Razorpay. Please try again.");
        }
        if (paidPaise != toPaise(tx.getAmount()) || invoice.getTotal() == null || paidPaise != toPaise(invoice.getTotal())) {
            log.error("Renewal amount mismatch: order {} paid {} paise, invoice {} total {}", orderId, paidPaise, invoice.getId(), invoice.getTotal());
            throw new BadRequestException("Payment amount does not match the invoice. Our team will reconcile it - please contact support.");
        }

        tx.setRazorpayPaymentId(paymentId);
        tx.setRazorpaySignature(signature);
        tx.setPaymentMethod(payment.optString("method", null));
        tx.setStatus(PaymentTransactionStatus.VERIFIED);
        tx.setPaidAt(LocalDateTime.now(BillingLifecycleService.IST));
        payments.save(tx);
        boolean newlyPaid = completeInvoice(invoice, tx);
        if (newlyPaid) {
            users.findById(userId).ifPresent(user -> emailInvoiceAfterCommit(invoice.getId(), companyId, user.getEmail(), user.getFullName()));
        }
        return verifiedResponse(invoice, !newlyPaid);
    }

    private boolean completeInvoice(Invoice invoice, PaymentTransaction tx) {
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            if (!Objects.equals(invoice.getRazorpayOrderId(), tx.getRazorpayOrderId())) {
                log.error("Duplicate renewal payment {} for already-paid invoice {} - refund required", tx.getRazorpayPaymentId(), invoice.getId());
            }
            return false;
        }
        return lifecycle.markInvoicePaid(invoice, tx, BillingLifecycleService.today());
    }

    private Map<String, Object> verifiedResponse(Invoice invoice, boolean alreadyProcessed) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "verified");
        response.put("alreadyProcessed", alreadyProcessed);
        response.put("invoiceId", invoice.getId());
        response.put("invoiceNumber", invoice.getInvoiceNumber());
        response.put("renewalDate", invoice.getSubscription() == null ? null : invoice.getSubscription().getRenewalDate());
        response.put("message", "Payment received. Your subscription is active.");
        return response;
    }

    // ------------------------------------------------------------- webhooks

    /** payment.captured / payment.authorized / payment.failed for an order created by {@link #createRenewalOrder}. */
    @Transactional
    public void handleRenewalPaymentEvent(String event, PaymentTransaction tx, JSONObject paymentEntity) {
        String paymentId = paymentEntity.optString("id", "");
        tx.setWebhookEvent(event);
        if ("payment.failed".equalsIgnoreCase(event)) {
            if (tx.getStatus() != PaymentTransactionStatus.VERIFIED) {
                tx.setStatus(PaymentTransactionStatus.FAILED);
                tx.setRazorpayPaymentId(paymentId.isBlank() ? tx.getRazorpayPaymentId() : paymentId);
                payments.save(tx);
                if (tx.getSubscription() != null) notifier.paymentFailed(tx.getSubscription(), tx.getId());
            }
            return;
        }
        if (!"payment.captured".equalsIgnoreCase(event) && !"payment.authorized".equalsIgnoreCase(event)) return;
        if (tx.getInvoiceId() == null) return;
        Invoice invoice = invoices.findById(tx.getInvoiceId()).orElse(null);
        if (invoice == null) return;
        long paid = paymentEntity.optLong("amount", -1L);
        if (paid != toPaise(tx.getAmount()) || invoice.getTotal() == null || paid != toPaise(invoice.getTotal())) {
            log.error("Webhook renewal amount mismatch for order {}: paid {} paise, invoice total {}", tx.getRazorpayOrderId(), paid, invoice.getTotal());
            tx.setStatus(PaymentTransactionStatus.CAPTURED);
            tx.setRazorpayPaymentId(paymentId);
            payments.save(tx);
            return;
        }
        if (tx.getStatus() != PaymentTransactionStatus.VERIFIED) {
            tx.setRazorpayPaymentId(paymentId);
            tx.setPaymentMethod(paymentEntity.optString("method", null));
            tx.setStatus(PaymentTransactionStatus.VERIFIED);
            tx.setPaidAt(LocalDateTime.now(BillingLifecycleService.IST));
            payments.save(tx);
        }
        if (completeInvoice(invoice, tx) && invoice.getCompany() != null) {
            notifier.companyAdmins(invoice.getCompany().getId()).stream().findFirst().ifPresent(admin ->
                    emailInvoiceAfterCommit(invoice.getId(), invoice.getCompany().getId(), admin.getEmail(), admin.getFullName()));
        }
    }

    /**
     * Razorpay Subscriptions events: subscription.charged (auto-debit
     * succeeded), subscription.halted (all retries failed), subscription.pending
     * (a charge failed, retrying), subscription.cancelled / completed.
     */
    @Transactional
    public void handleSubscriptionEvent(String event, JSONObject payload) {
        JSONObject subEntity = entity(payload, "subscription");
        if (subEntity == null) return;
        String rzpSubscriptionId = subEntity.optString("id", "");
        Subscription sub = subscriptions.findFirstByRazorpaySubscriptionIdAndDeletedFalse(rzpSubscriptionId).orElse(null);
        if (sub == null) {
            log.warn("Razorpay subscription event {} for unknown subscription {}", event, rzpSubscriptionId);
            return;
        }
        LocalDate today = BillingLifecycleService.today();
        switch (event) {
            case "subscription.charged" -> {
                JSONObject payment = entity(payload, "payment");
                if (payment == null) return;
                String paymentId = payment.optString("id", "");
                if (paymentId.isBlank() || payments.findByRazorpayPaymentId(paymentId).isPresent()) return;
                Invoice invoice = lifecycle.ensureDueInvoice(sub, today);
                BigDecimal charged = BigDecimal.valueOf(payment.optLong("amount", 0L), 2);
                PaymentTransaction tx = new PaymentTransaction();
                tx.setCompany(sub.getCompany());
                tx.setSubscription(sub);
                tx.setPlan(invoice.getPlan());
                String orderId = payment.optString("order_id", "");
                tx.setRazorpayOrderId(orderId.isBlank() ? "sub-" + paymentId : orderId);
                tx.setRazorpayPaymentId(paymentId);
                tx.setCurrency(payment.optString("currency", gateway.currency()));
                tx.setAmount(charged);
                tx.setStatus(PaymentTransactionStatus.VERIFIED);
                tx.setPaymentMethod(payment.optString("method", null));
                tx.setPaidAt(LocalDateTime.now(BillingLifecycleService.IST));
                tx.setPurpose(PaymentPurpose.AUTO_DEBIT);
                tx.setInvoiceId(invoice.getId());
                tx.setWebhookEvent(event);
                payments.save(tx);
                if (invoice.getTotal() != null && charged.compareTo(invoice.getTotal()) != 0) {
                    invoice.setNotes("Auto-debit charged INR " + charged + " (seat count changed since the mandate); "
                            + "the difference is adjusted on the next invoice.");
                    log.warn("Auto-debit amount {} differs from invoice {} total {}", charged, invoice.getId(), invoice.getTotal());
                }
                completeInvoice(invoice, tx);
            }
            case "subscription.pending" -> notifier.paymentFailed(sub, sub.getId());
            case "subscription.halted" -> {
                if (sub.getStatus() == SubscriptionStatus.ACTIVE) {
                    sub.setStatus(SubscriptionStatus.PAST_DUE);
                    sub.setPastDueSince(today);
                    sub.setGraceEndsOn(today.plusDays(settings.graceDays()));
                    subscriptions.save(sub);
                    lifecycle.auditFor(sub, "AUTO_DEBIT_HALTED", "Razorpay halted auto-debit; past due until " + sub.getGraceEndsOn());
                }
                notifier.pastDue(sub, lifecycle.pendingInvoice(sub).orElse(null));
            }
            case "subscription.cancelled", "subscription.completed" -> {
                sub.setRazorpaySubscriptionId(null);
                subscriptions.save(sub);
                lifecycle.auditFor(sub, "AUTO_DEBIT_ENDED", "Razorpay subscription " + rzpSubscriptionId + " " + event);
            }
            default -> { /* activated, authenticated, updated: nothing to do */ }
        }
    }

    // --------------------------------------------------------- auto-debit

    /**
     * Starts a Razorpay Subscription (mandate) for the current plan: a plan
     * per (plan, cycle, GST-inclusive per-seat price) and quantity = seats,
     * first charge on the next renewal date. Returns what the checkout needs.
     */
    @Transactional
    public Map<String, Object> startAutoDebit(Long companyId) {
        if (!settings.razorpaySubscriptionsEnabled()) {
            throw new BadRequestException("Auto-debit is not enabled yet. Use Pay now for renewals.");
        }
        Subscription sub = subscription(companyId);
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BadRequestException("Pay the current invoice first; auto-debit can be set up on an active subscription.");
        }
        if (sub.getRazorpaySubscriptionId() != null) {
            throw new BadRequestException("Auto-debit is already set up.");
        }
        String plan = BillingLifecycleService.effectivePlan(sub);
        String cycle = BillingLifecycleService.effectiveCycle(sub);
        BigDecimal rate = BillingPricing.rateFor(plan, cycle);
        var company = sub.getCompany();
        BigDecimal perSeat = GstCalculator.compute(rate, settings.sellerGstin(), settings.sellerStateCode(),
                company.getBillingStateCode(), company.getGstin()).total();
        int months = BillingLifecycleService.cycleMonths(cycle);
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("companyId", String.valueOf(companyId));
        notes.put("subscriptionId", String.valueOf(sub.getId()));
        String planId = gateway.createPlan("monthly", months, "Vettri HRMS " + plan + " (" + cycle.toLowerCase() + ", per seat)",
                toPaise(perSeat), notes);
        int seats = Math.max(1, sub.getPeriodPeakSeats() != null ? sub.getPeriodPeakSeats()
                : sub.getBillableEmployeeCount() == null ? 1 : sub.getBillableEmployeeCount());
        Long startAt = sub.getRenewalDate() == null ? null
                : sub.getRenewalDate().atStartOfDay(BillingLifecycleService.IST).toEpochSecond();
        JSONObject created = gateway.createSubscription(planId, seats, Math.max(1, 120 / months), startAt, notes);
        sub.setRazorpayPlanId(planId);
        sub.setRazorpaySubscriptionId(created.optString("id", null));
        subscriptions.save(sub);
        lifecycle.auditFor(sub, "AUTO_DEBIT_STARTED", "Razorpay subscription " + sub.getRazorpaySubscriptionId() + " for " + seats + " seat(s)");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("key", gateway.keyId());
        response.put("subscriptionId", sub.getRazorpaySubscriptionId());
        response.put("shortUrl", created.optString("short_url", null));
        return response;
    }

    @Transactional
    public void cancelAutoDebit(Long companyId) {
        Subscription sub = subscription(companyId);
        if (sub.getRazorpaySubscriptionId() == null) return;
        gateway.cancelSubscription(sub.getRazorpaySubscriptionId());
        lifecycle.auditFor(sub, "AUTO_DEBIT_CANCELLED", "Razorpay subscription " + sub.getRazorpaySubscriptionId() + " cancelled");
        sub.setRazorpaySubscriptionId(null);
        subscriptions.save(sub);
    }

    // ------------------------------------------------------------- helpers

    private Subscription subscription(Long companyId) {
        return subscriptions.findByCompany_IdAndDeletedFalse(companyId)
                .orElseThrow(() -> new BadRequestException("No subscription found for this company."));
    }

    static long toPaise(BigDecimal amount) {
        return amount == null ? 0L : amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private static JSONObject entity(JSONObject payload, String name) {
        if (payload == null) return null;
        JSONObject wrapper = payload.optJSONObject(name);
        return wrapper == null ? null : wrapper.optJSONObject("entity");
    }

    private void emailInvoiceAfterCommit(Long invoiceId, Long companyId, String email, String name) {
        if (invoiceService == null || email == null) return;
        Runnable send = () -> {
            try {
                invoiceService.email(invoiceId, companyId, email, name);
            } catch (RuntimeException ex) {
                log.warn("Renewal paid but invoice email failed. invoiceId={}", invoiceId, ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
