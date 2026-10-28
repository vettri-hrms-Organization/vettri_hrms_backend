package com.haodaone.billing.service;

import com.haodaone.billing.entity.Invoice;
import com.haodaone.company.entity.Subscription;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Billing notifications to every active COMPANY_ADMIN of a company, in-app
 * plus email (NotificationService emails after commit unless the admin opted
 * out of workflow emails). Each notification carries a dedupe key
 * (type:entity:id), so re-running the daily job never sends a reminder twice.
 */
@Component
public class BillingNotifier {

    private static final Logger log = LoggerFactory.getLogger(BillingNotifier.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final NotificationService notifications;
    private final UserRepository users;
    private final BillingSettings settings;

    public BillingNotifier(NotificationService notifications, UserRepository users, BillingSettings settings) {
        this.notifications = notifications;
        this.users = users;
        this.settings = settings;
    }

    public void trialEnding(Subscription sub, long daysLeft) {
        send(sub, "BILLING_TRIAL_END_T" + daysLeft, "Subscription", sub.getId(), "HIGH",
                "Your Vettri HRMS trial ends in " + daysLeft + (daysLeft == 1 ? " day" : " days"),
                "Your free trial ends on " + date(sub.getRenewalDate()) + ". Choose a plan and pay to keep full access: "
                        + settings.billingUrl());
    }

    public void trialEnded(Subscription sub, Invoice due) {
        send(sub, "BILLING_TRIAL_ENDED", "Subscription", sub.getId(), "HIGH",
                "Your Vettri HRMS trial has ended",
                "Your workspace is now read-only. Pay " + amount(due) + " to restore full access: " + settings.billingUrl());
    }

    public void renewalReminder(Subscription sub, Invoice due, long daysLeft) {
        boolean autoDebit = sub.getRazorpaySubscriptionId() != null && settings.razorpaySubscriptionsEnabled();
        send(sub, "BILLING_RENEWAL_T" + daysLeft, "Invoice", due.getId(), "NORMAL",
                "Subscription renews in " + daysLeft + (daysLeft == 1 ? " day" : " days"),
                (autoDebit
                        ? "Your subscription renews on " + date(sub.getRenewalDate()) + " and " + amount(due) + " will be auto-debited."
                        : "Your subscription renews on " + date(sub.getRenewalDate()) + ". Amount due: " + amount(due) + ". Pay now: ")
                        + (autoDebit ? "" : settings.billingUrl()));
    }

    public void pastDue(Subscription sub, Invoice due) {
        send(sub, "BILLING_PAST_DUE", "Invoice", due == null ? sub.getId() : due.getId(), "HIGH",
                "Payment overdue - action required",
                "Your renewal payment of " + amount(due) + " is overdue. Pay by " + date(sub.getGraceEndsOn())
                        + " to avoid your workspace becoming read-only: " + settings.billingUrl());
    }

    public void suspensionWarning(Subscription sub, Invoice due) {
        send(sub, "BILLING_SUSPEND_WARNING", "Invoice", due == null ? sub.getId() : due.getId(), "HIGH",
                "Your workspace becomes read-only tomorrow",
                "We have not received your renewal payment of " + amount(due) + ". Pay today to avoid suspension: "
                        + settings.billingUrl());
    }

    public void suspended(Subscription sub, Invoice due) {
        send(sub, "BILLING_SUSPENDED", "Invoice", due == null ? sub.getId() : due.getId(), "HIGH",
                "Workspace suspended (read-only)",
                "Your subscription is suspended for non-payment. Data is safe and viewable; pay " + amount(due)
                        + " to restore full access: " + settings.billingUrl());
    }

    public void cancelled(Subscription sub) {
        send(sub, "BILLING_CANCELLED", "Subscription", sub.getId(), "HIGH",
                "Subscription cancelled",
                "Your subscription ended on " + date(sub.getRenewalDate()) + " and the workspace is now read-only. "
                        + "Pay the renewal invoice any time to reactivate: " + settings.billingUrl());
    }

    public void paymentFailed(Subscription sub, Long paymentTransactionId) {
        send(sub, "BILLING_PAYMENT_FAILED", "PaymentTransaction", paymentTransactionId, "HIGH",
                "Subscription payment failed",
                "A subscription payment could not be completed. Please retry with another method: " + settings.billingUrl());
    }

    public void paymentReceived(Subscription sub, Invoice invoice) {
        send(sub, "BILLING_PAYMENT_RECEIVED", "Invoice", invoice.getId(), "NORMAL",
                "Payment received - thank you",
                "We received " + amount(invoice) + " for invoice " + invoice.getInvoiceNumber()
                        + ". Your subscription is active until " + date(sub.getRenewalDate() == null ? null : sub.getRenewalDate().minusDays(1)) + ".");
    }

    private void send(Subscription sub, String type, String entityType, Long entityId, String priority, String title, String message) {
        if (sub == null || sub.getCompany() == null || notifications == null) return;
        Long companyId = sub.getCompany().getId();
        for (User admin : companyAdmins(companyId)) {
            try {
                notifications.notifyUser(companyId, admin.getId(), type, title, message, entityType, entityId, priority);
            } catch (RuntimeException ex) {
                log.warn("Billing notification {} failed for company {} user {}", type, companyId, admin.getId(), ex);
            }
        }
    }

    public List<User> companyAdmins(Long companyId) {
        if (users == null || companyId == null) return List.of();
        return users.findAllByCompanyIdAndDeletedFalse(companyId).stream()
                .filter(User::isActive)
                .filter(user -> user.getRoles() != null && user.getRoles().stream().anyMatch(role -> "COMPANY_ADMIN".equals(role.getName())))
                .toList();
    }

    private static String date(LocalDate date) {
        return date == null ? "-" : DATE.format(date);
    }

    private static String amount(Invoice invoice) {
        BigDecimal total = invoice == null ? null : invoice.getTotal();
        if (total == null) return "the amount due";
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "INR " + format.format(total);
    }
}
