package com.haodaone.billing.service;

import com.haodaone.billing.entity.PaymentTransaction;
import com.haodaone.billing.repository.PaymentTransactionRepository;
import com.haodaone.company.entity.Company;
import com.haodaone.company.entity.Plan;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.entity.SubscriptionStatus;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.repository.SubscriptionRepository;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The ₹1 signup payment starts the trial, and that is when the welcome email (with the overview deck) goes out. */
class SignupWelcomeEmailTest {

    private static final String SECRET = "test-webhook-secret";

    private final UserRepository users = mock(UserRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final BillingService service = new BillingService(users, companies, subscriptions, payments, null, email, null);

    private Subscription pendingSignup(SubscriptionStatus status) {
        Company company = new Company();
        company.setId(9L);
        company.setName("Acme Tech");
        Subscription subscription = new Subscription();
        subscription.setCompany(company);
        subscription.setPlan(Plan.STARTER);
        subscription.setStatus(status);
        subscription.setEmployeeLimit(25);
        subscription.setBillingCycle("MONTHLY");
        subscription.setStartDate(LocalDate.of(2026, 10, 29));
        subscription.setRenewalDate(LocalDate.of(2026, 11, 28));
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setCompany(company);
        transaction.setSubscription(subscription);
        when(payments.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(transaction));
        User owner = new User();
        owner.setEmail("owner@acme.test");
        owner.setFullName("Asha Rao");
        when(users.findAllByCompanyIdAndDeletedFalse(9L)).thenReturn(List.of(owner));
        ReflectionTestUtils.setField(service, "razorpayWebhookSecret", SECRET);
        return subscription;
    }

    private void deliverCapturedPayment() throws Exception {
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_1\",\"order_id\":\"order_1\",\"amount\":100,\"currency\":\"INR\",\"method\":\"upi\"}}}}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        service.processWebhookEvent(payload, signature);
    }

    @Test
    void capturedSignupPaymentStartsTrialAndSendsWelcomeOnce() throws Exception {
        pendingSignup(SubscriptionStatus.PENDING_PAYMENT);

        deliverCapturedPayment();

        verify(email, times(1)).sendWelcomeEmail(eq("owner@acme.test"), eq("Asha Rao"), eq("Acme Tech"), eq("STARTER"),
                eq(25), eq("MONTHLY"), eq("2026-10-29"), eq("2026-11-28"));
    }

    @Test
    void paymentForAnAlreadyStartedTrialDoesNotResendWelcome() throws Exception {
        pendingSignup(SubscriptionStatus.TRIALING);

        deliverCapturedPayment();

        verify(email, never()).sendWelcomeEmail(anyString(), anyString(), anyString(), any(), any(), any(), any(), any());
    }
}
