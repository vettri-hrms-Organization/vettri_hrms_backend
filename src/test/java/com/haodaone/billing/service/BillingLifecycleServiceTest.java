package com.haodaone.billing.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.billing.entity.Invoice;
import com.haodaone.billing.entity.InvoiceStatus;
import com.haodaone.billing.entity.InvoiceType;
import com.haodaone.billing.entity.PaymentTransaction;
import com.haodaone.billing.entity.SeatAddOnCharge;
import com.haodaone.billing.repository.InvoiceRepository;
import com.haodaone.billing.repository.SeatAddOnChargeRepository;
import com.haodaone.company.entity.Company;
import com.haodaone.company.entity.Plan;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.entity.SubscriptionStatus;
import com.haodaone.company.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BillingLifecycleServiceTest {

    private SubscriptionRepository subscriptions;
    private InvoiceRepository invoices;
    private SeatAddOnChargeRepository addOns;
    private SeatCountService seats;
    private InvoiceNumberService numbers;
    private BillingNotifier notifier;
    private BillingLifecycleService lifecycle;
    private final List<Invoice> stored = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        subscriptions = mock(SubscriptionRepository.class);
        invoices = mock(InvoiceRepository.class);
        addOns = mock(SeatAddOnChargeRepository.class);
        seats = mock(SeatCountService.class);
        numbers = mock(InvoiceNumberService.class);
        notifier = mock(BillingNotifier.class);
        when(subscriptions.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoices.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice invoice = inv.getArgument(0);
            if (invoice.getId() == null) {
                invoice.setId(ids.incrementAndGet());
                stored.add(invoice);
            }
            return invoice;
        });
        when(invoices.findFirstBySubscription_IdAndStatusAndInvoiceTypeAndDeletedFalseOrderByIdDesc(anyLong(), eq(InvoiceStatus.PENDING), eq(InvoiceType.SUBSCRIPTION)))
                .thenAnswer(inv -> stored.stream().filter(i -> i.getStatus() == InvoiceStatus.PENDING).reduce((a, b) -> b));
        when(addOns.save(any(SeatAddOnCharge.class))).thenAnswer(inv -> inv.getArgument(0));
        when(seats.billableSeats(anyLong())).thenReturn(10);
        when(numbers.nextInvoiceNumber(any())).thenReturn("VTR/2026-27/00001");
        BillingSettings settings = new BillingSettings("Vettri HRMS", "33AAACH7409R1Z8", "Chennai", "33", 7, 0);
        lifecycle = new BillingLifecycleService(subscriptions, invoices, addOns, seats, numbers, settings, notifier,
                mock(AuditLogService.class), null);
    }

    private Subscription subscription(SubscriptionStatus status, LocalDate renewal) {
        Company company = new Company();
        company.setId(7L);
        company.setName("Acme");
        company.setBillingStateCode("29");
        Subscription sub = new Subscription();
        sub.setId(1L);
        sub.setCompany(company);
        sub.setPlan(Plan.STARTER);
        sub.setStatus(status);
        sub.setBillingCycle("MONTHLY");
        sub.setRate(new BigDecimal("199"));
        sub.setBillableEmployeeCount(10);
        sub.setEmployeeLimit(1000);
        sub.setStartDate(renewal.minusMonths(1));
        sub.setRenewalDate(renewal);
        return sub;
    }

    @Test
    void trialEndsUnpaidThenExpiresReadOnlyWithADueInvoice() {
        LocalDate trialEnd = LocalDate.of(2026, 11, 30);
        Subscription sub = subscription(SubscriptionStatus.TRIALING, trialEnd);

        lifecycle.evaluate(sub, trialEnd.minusDays(3));
        verify(notifier).trialEnding(sub, 3L);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.TRIALING);

        lifecycle.evaluate(sub, trialEnd);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(BillingLifecycleService.isReadOnly(sub.getStatus())).isTrue();
        Invoice due = stored.get(0);
        assertThat(due.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        // 10 seats x 199 = 1990, Karnataka buyer vs Tamil Nadu seller -> IGST 18%
        assertThat(due.getTaxableValue()).isEqualByComparingTo("1990.00");
        assertThat(due.getIgstAmount()).isEqualByComparingTo("358.20");
        assertThat(due.getTotal()).isEqualByComparingTo("2348.20");
    }

    @Test
    void activeRenewalGoesPastDueThenSuspendedAfterGraceAndPaymentRestores() {
        LocalDate renewal = LocalDate.of(2026, 11, 30);
        Subscription sub = subscription(SubscriptionStatus.ACTIVE, renewal);

        lifecycle.evaluate(sub, renewal.minusDays(10));
        assertThat(stored).isEmpty(); // nothing before T-7

        lifecycle.evaluate(sub, renewal.minusDays(7));
        assertThat(stored).hasSize(1);
        verify(notifier).renewalReminder(eq(sub), any(Invoice.class), eq(7L));
        lifecycle.evaluate(sub, renewal.minusDays(7)); // idempotent re-run
        assertThat(stored).hasSize(1);

        lifecycle.evaluate(sub, renewal);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(sub.getGraceEndsOn()).isEqualTo(renewal.plusDays(7));

        lifecycle.evaluate(sub, renewal.plusDays(7));
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE); // still in grace

        LocalDate suspendDay = renewal.plusDays(8);
        lifecycle.evaluate(sub, suspendDay);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
        verify(notifier).suspended(eq(sub), any(Invoice.class));

        Invoice due = stored.get(0);
        PaymentTransaction tx = new PaymentTransaction();
        tx.setRazorpayOrderId("order_1");
        tx.setRazorpayPaymentId("pay_1");
        LocalDate payDay = renewal.plusDays(10);
        assertThat(lifecycle.markInvoicePaid(due, tx, payDay)).isTrue();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getSuspendedAt()).isNull();
        assertThat(sub.getGraceEndsOn()).isNull();
        // suspended period is not charged: the paid month starts on the payment date
        assertThat(sub.getStartDate()).isEqualTo(payDay);
        assertThat(sub.getRenewalDate()).isEqualTo(payDay.plusMonths(1));
        assertThat(due.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(due.getInvoiceNumber()).isEqualTo("VTR/2026-27/00001");
        // paying twice is a no-op
        assertThat(lifecycle.markInvoicePaid(due, tx, payDay)).isFalse();
        verify(numbers, times(1)).nextInvoiceNumber(any());
    }

    @Test
    void earlyPaymentExtendsFromTheCurrentRenewalDate() {
        LocalDate renewal = LocalDate.of(2026, 11, 30);
        Subscription sub = subscription(SubscriptionStatus.ACTIVE, renewal);
        lifecycle.evaluate(sub, renewal.minusDays(5));
        lifecycle.markInvoicePaid(stored.get(0), new PaymentTransaction(), renewal.minusDays(4));
        assertThat(sub.getRenewalDate()).isEqualTo(renewal.plusMonths(1));
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void midCycleSeatIncreaseAddsAProratedChargeOnce() {
        LocalDate renewal = LocalDate.of(2026, 12, 1);
        Subscription sub = subscription(SubscriptionStatus.ACTIVE, renewal);
        sub.setStartDate(LocalDate.of(2026, 11, 1)); // 30-day period
        when(invoices.existsBySubscription_IdAndInvoiceTypeAndStatusAndDeletedFalse(1L, InvoiceType.SUBSCRIPTION, InvoiceStatus.PAID)).thenReturn(true);
        when(seats.billableSeats(7L)).thenReturn(12);

        Optional<SeatAddOnCharge> charge = lifecycle.recordSeatAddOns(sub, LocalDate.of(2026, 11, 16)); // 15 days left
        assertThat(charge).isPresent();
        assertThat(charge.get().getSeats()).isEqualTo(2);
        assertThat(charge.get().getAmount()).isEqualByComparingTo("199.00"); // 199 x 2 x 15/30
        assertThat(sub.getPeriodPeakSeats()).isEqualTo(12);
        assertThat(lifecycle.recordSeatAddOns(sub, LocalDate.of(2026, 11, 17))).isEmpty();
    }
}
