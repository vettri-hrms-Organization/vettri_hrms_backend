package com.haodaone.billing.service;

import com.haodaone.auth.dto.LoginRequest;
import com.haodaone.auth.dto.LoginResponse;
import com.haodaone.auth.service.AuthService;
import com.haodaone.billing.dto.PlanPricingResponse;
import com.haodaone.billing.entity.PaymentTransaction;
import com.haodaone.billing.entity.PaymentTransactionStatus;
import com.haodaone.billing.repository.PaymentTransactionRepository;
import com.haodaone.company.entity.Company;
import com.haodaone.company.entity.Plan;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.entity.SubscriptionStatus;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.repository.SubscriptionRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class BillingService {

        private static final Map<String, PlanSpec> PLAN_PRICING = Map.of(
            "VETTRI_HRMS", new PlanSpec(Plan.VETTRI_HRMS, "Vettri HRMS", 1000, 500)
        );

    private final UserRepository users;
    private final CompanyRepository companies;
    private final SubscriptionRepository subscriptions;
    private final PaymentTransactionRepository paymentTransactions;
    private final AuthService authService;
    private final EmailService emailService;

    @Value("${app.razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${app.razorpay.key-secret:}")
    private String razorpayKeySecret;

    @Value("${app.razorpay.currency:INR}")
    private String razorpayCurrency;

    @Value("${app.razorpay.webhook-secret:}")
    private String razorpayWebhookSecret;

    @Autowired
    public BillingService(UserRepository users, CompanyRepository companies,
                          SubscriptionRepository subscriptions,
                          PaymentTransactionRepository paymentTransactions,
                          AuthService authService, EmailService emailService) {
        this.users = users;
        this.companies = companies;
        this.subscriptions = subscriptions;
        this.paymentTransactions = paymentTransactions;
        this.authService = authService;
        this.emailService = emailService;
    }

    BillingService(UserRepository users, CompanyRepository companies,
                   SubscriptionRepository subscriptions,
                   PaymentTransactionRepository paymentTransactions,
                   AuthService authService) {
        this(users, companies, subscriptions, paymentTransactions, authService, null);
    }

    public List<PlanPricingResponse> pricing() {
        return PLAN_PRICING.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new PlanPricingResponse(
                        entry.getKey(),
                        entry.getValue().label(),
                        BillingPricing.rateFor(entry.getKey(), BillingPricing.MONTHLY),
                        razorpayCurrency,
                        entry.getValue().employeeLimit(),
                        entry.getValue().deviceLimit(),
                        "Plan for " + entry.getValue().label() + " companies"
                )).toList();
    }

    @Transactional
    public Map<String, Object> createOrder(Long companyId, Long userId, String plan, String billingCycle, Integer employeeCount) {
        if (companyId == null || userId == null) {
            throw new BadRequestException("A company and user are required for checkout.");
        }

        String normalizedPlan = BillingPricing.normalizePlan(plan);
        String normalizedCycle = BillingPricing.normalizeBillingCycle(billingCycle);
        if (normalizedPlan == null) {
            throw new BadRequestException("Plan not found.");
        }
        PlanSpec spec = PLAN_PRICING.get(normalizedPlan);
        if (spec == null) {
            throw new BadRequestException("Plan not found.");
        }

        Company company = companies.findById(companyId)
                .orElseThrow(() -> new BadRequestException("Company not found."));
        User user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found."));

        if (user.getCompany() != null && !Objects.equals(user.getCompany().getId(), companyId)) {
            throw new BadRequestException("Company mismatch for payment checkout.");
        }

        if (razorpayKeyId == null || razorpayKeyId.isBlank() || razorpayKeySecret == null || razorpayKeySecret.isBlank()) {
            throw new BadRequestException("Payment is temporarily unavailable. Add Razorpay credentials to the backend environment.");
        }

        int normalizedEmployees = BillingPricing.normalizeEmployeeCount(employeeCount);
        BigDecimal subscriptionAmount = calculateSubscriptionAmount(normalizedPlan, normalizedEmployees, normalizedCycle);
        BigDecimal verificationAmount = calculateVerificationAmount();

        try {
            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject orderRequest = new JSONObject();
            int amountPaise = verificationAmount.multiply(new BigDecimal("100")).intValueExact();
            orderRequest.put("amount", amountPaise);
            orderRequest.put("currency", razorpayCurrency);
            orderRequest.put("receipt", "vettri-" + company.getId() + "-" + System.currentTimeMillis());
            orderRequest.put("notes", new JSONObject(Map.of(
                    "companyId", String.valueOf(company.getId()),
                    "userId", String.valueOf(user.getId()),
                    "plan", normalizedPlan,
                    "billingCycle", normalizedCycle,
                    "employeeCount", String.valueOf(normalizedEmployees),
                    "organizationName", company.getName(),
                    "customerEmail", user.getEmail()
            )));

            Order order = client.orders.create(orderRequest);
            String razorpayOrderId = order.get("id");

            PaymentTransaction tx = paymentTransactions.findByRazorpayOrderId(razorpayOrderId)
                    .orElseGet(PaymentTransaction::new);
            tx.setCompany(company);
            tx.setPlan(normalizedPlan);
            tx.setRazorpayOrderId(razorpayOrderId);
            tx.setRazorpayPaymentId(null);
            tx.setCurrency(razorpayCurrency);
            tx.setAmount(verificationAmount);
            tx.setStatus(PaymentTransactionStatus.CREATED);
            tx.setPaymentMethod(null);
            tx.setPaidAt(null);
            paymentTransactions.save(tx);

            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("key", razorpayKeyId);
            response.put("orderId", razorpayOrderId);
            response.put("amount", amountPaise);
            response.put("currency", razorpayCurrency);
            response.put("plan", normalizedPlan);
            response.put("billingCycle", normalizedCycle);
            response.put("employeeCount", normalizedEmployees);
            response.put("subscriptionAmount", subscriptionAmount);
            response.put("verificationAmount", verificationAmount);
            response.put("companyId", company.getId());
            response.put("userId", user.getId());
            response.put("organizationName", company.getName());
            response.put("customerName", user.getFullName());
            response.put("customerEmail", user.getEmail());
            return response;
        } catch (RazorpayException ex) {
            throw new BadRequestException("Unable to create a Razorpay order: " + ex.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> verifyPayment(Long companyId, Long userId, String plan, String billingCycle, Integer employeeCount,
                                            String razorpayPaymentId, String razorpayOrderId, String razorpaySignature) {
        if (companyId == null || userId == null) {
            throw new BadRequestException("Payment verification is missing a user or company.");
        }

        String normalizedPlan = BillingPricing.normalizePlan(plan);
        String normalizedCycle = BillingPricing.normalizeBillingCycle(billingCycle);
        if (normalizedPlan == null) {
            throw new BadRequestException("Plan not found.");
        }

        Company company = companies.findById(companyId)
                .orElseThrow(() -> new BadRequestException("Company not found."));
        User user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found."));

        if (user.getCompany() != null && !Objects.equals(user.getCompany().getId(), companyId)) {
            throw new BadRequestException("Company mismatch for payment verification.");
        }

        if (razorpayPaymentId == null || razorpayOrderId == null || razorpaySignature == null || razorpaySignature.isBlank()) {
            throw new BadRequestException("Payment verification failed. Missing Razorpay data.");
        }

        String expectedSignature = generateSignature(razorpayOrderId + "|" + razorpayPaymentId);
        if (!expectedSignature.equalsIgnoreCase(razorpaySignature)) {
            throw new BadRequestException("Payment verification failed. Signature mismatch.");
        }

        PlanSpec spec = PLAN_PRICING.get(normalizedPlan);
        if (spec == null) {
            throw new BadRequestException("Plan not found.");
        }

        RazorpayClient client;
        Order orderRecord;
        try {
            client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            orderRecord = client.orders.fetch(razorpayOrderId);
        } catch (RazorpayException ex) {
            throw new BadRequestException("Razorpay order not found for verification.");
        }

        JSONObject orderJson = new JSONObject(orderRecord.toString());
        if (orderJson.has("notes")) {
            JSONObject notes = orderJson.optJSONObject("notes");
            if (notes != null) {
                String orderCompanyId = notes.optString("companyId", "");
                String orderUserId = notes.optString("userId", "");
                String orderPlan = notes.optString("plan", "");
                String orderCycle = notes.optString("billingCycle", "");
                int normalizedEmployees = BillingPricing.normalizeEmployeeCount(employeeCount);
                String orderEmployees = notes.optString("employeeCount", "");
                if (!orderCompanyId.equals(String.valueOf(companyId)) || !orderUserId.equals(String.valueOf(userId))
                    || !orderPlan.equals(normalizedPlan) || !orderCycle.equals(normalizedCycle)
                    || !orderEmployees.equals(String.valueOf(normalizedEmployees))) {
                    throw new BadRequestException("Payment order does not belong to the current company or user.");
                }
            }
        }

        int normalizedEmployees = BillingPricing.normalizeEmployeeCount(employeeCount);
        BigDecimal subscriptionAmount = calculateSubscriptionAmount(normalizedPlan, normalizedEmployees, normalizedCycle);
        BigDecimal verificationAmount = calculateVerificationAmount();
        int expectedAmountPaise = verificationAmount.multiply(new BigDecimal("100")).intValueExact();
        int orderAmountPaise = orderJson.optInt("amount", 0);
        if (orderAmountPaise != expectedAmountPaise) {
            throw new BadRequestException("Payment amount mismatch for this plan and billing cycle.");
        }

        PaymentTransaction paymentTransaction = paymentTransactions.findByRazorpayOrderId(razorpayOrderId)
                .or(() -> paymentTransactions.findByRazorpayPaymentId(razorpayPaymentId))
                .orElseGet(PaymentTransaction::new);

        if (paymentTransaction.getId() != null && PaymentTransactionStatus.VERIFIED.equals(paymentTransaction.getStatus())) {
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("status", "verified");
            response.put("alreadyProcessed", true);
            response.put("companyId", company.getId());
            response.put("userId", user.getId());
            response.put("plan", normalizedPlan);
            response.put("billingCycle", normalizedCycle);
            response.put("message", "Payment already verified and subscription is active.");
            return response;
        }

        String paymentMethod = null;
        try {
            Payment payment = client.payments.fetch(razorpayPaymentId);
            Object paymentMethodValue = payment.get("method");
            Object paymentStatusValue = payment.get("status");
                Object paymentAmountValue = payment.get("amount");
                int paymentAmountPaise = paymentAmountValue instanceof Number number
                    ? number.intValue()
                    : paymentAmountValue == null ? 0 : Integer.parseInt(String.valueOf((Object) paymentAmountValue));
            if (paymentAmountPaise != expectedAmountPaise) {
                throw new BadRequestException("Payment amount mismatch for this plan and billing cycle.");
            }
            paymentMethod = paymentMethodValue != null ? String.valueOf(paymentMethodValue) : null;
            if (paymentStatusValue != null && "failed".equalsIgnoreCase(String.valueOf(paymentStatusValue))) {
                notifyPaymentFailure(user, company, razorpayOrderId);
                throw new BadRequestException("Payment failed in Razorpay.");
            }
        } catch (RazorpayException ex) {
            notifyPaymentFailure(user, company, razorpayOrderId);
            throw new BadRequestException("Razorpay payment not found or inaccessible.");
        }

        Optional<Subscription> existing = subscriptions.findByCompany_IdAndDeletedFalse(companyId);
        Subscription subscription = existing.orElseGet(() -> {
            Subscription created = new Subscription();
            created.setCompany(company);
            created.setPlan(Plan.valueOf(normalizedPlan));
            created.setStatus(SubscriptionStatus.PENDING_PAYMENT);
            created.setEmployeeLimit(spec.employeeLimit());
            created.setDeviceLimit(spec.deviceLimit());
            created.setBillingCycle(normalizedCycle);
            created.setBillableEmployeeCount(normalizedEmployees);
            created.setStartDate(LocalDate.now());
            created.setRenewalDate(LocalDate.now().plusDays(30));
            created.setRate(BillingPricing.rateFor(normalizedPlan, normalizedCycle));
            created.setAmount(subscriptionAmount);
            return created;
        });

        subscription.setCompany(company);
        subscription.setPlan(Plan.valueOf(normalizedPlan));
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setAmount(subscriptionAmount);
        subscription.setEmployeeLimit(spec.employeeLimit());
        subscription.setDeviceLimit(spec.deviceLimit());
        subscription.setBillingCycle(normalizedCycle);
        subscription.setBillableEmployeeCount(normalizedEmployees);
        subscription.setStartDate(LocalDate.now());
        subscription.setRenewalDate(LocalDate.now().plusDays(30));
        subscription.setRate(BillingPricing.rateFor(normalizedPlan, normalizedCycle));
        subscriptions.save(subscription);

        paymentTransaction.setCompany(company);
        paymentTransaction.setSubscription(subscription);
        paymentTransaction.setPlan(normalizedPlan);
        paymentTransaction.setRazorpayOrderId(razorpayOrderId);
        paymentTransaction.setRazorpayPaymentId(razorpayPaymentId);
        paymentTransaction.setRazorpaySignature(razorpaySignature);
        paymentTransaction.setCurrency(razorpayCurrency);
        paymentTransaction.setAmount(verificationAmount);
        paymentTransaction.setStatus(PaymentTransactionStatus.VERIFIED);
        paymentTransaction.setPaymentMethod(paymentMethod);
        paymentTransaction.setPaidAt(LocalDateTime.now());
        paymentTransactions.save(paymentTransaction);

        user.setAccountStatus("ACTIVE");
        user.setActive(true);
        users.save(user);

        String customerEmail = user.getEmail();
        String customerName = user.getFullName();
        String organizationName = company.getName();
        String paymentDate = paymentTransaction.getPaidAt().toString();
        afterCommit(() -> emailService.sendPaymentSuccessEmail(customerEmail, customerName, organizationName,
            normalizedPlan, normalizedEmployees, normalizedCycle, verificationAmount, paymentDate,
            razorpayOrderId, razorpayPaymentId));

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("status", "verified");
        response.put("companyId", company.getId());
        response.put("userId", user.getId());
        response.put("plan", normalizedPlan);
        response.put("billingCycle", normalizedCycle);
        response.put("employeeCount", normalizedEmployees);
        response.put("message", "Payment verified and company activated.");
        return response;
    }

    @Transactional
    public void processWebhookEvent(String rawPayload, String signature) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new BadRequestException("Missing Razorpay webhook payload.");
        }
        if (signature == null || signature.isBlank()) {
            throw new BadRequestException("Missing Razorpay webhook signature.");
        }
        if (razorpayWebhookSecret == null || razorpayWebhookSecret.isBlank()) {
            throw new BadRequestException("Razorpay webhook secret is not configured.");
        }

        String expectedSignature = generateSignature(rawPayload, razorpayWebhookSecret);
        if (!expectedSignature.equalsIgnoreCase(signature)) {
            throw new BadRequestException("Invalid Razorpay webhook signature.");
        }

        JSONObject payload = new JSONObject(rawPayload);
        String event = payload.optString("event", "");
        JSONObject paymentPayload = payload.optJSONObject("payload");
        JSONObject paymentData = paymentPayload != null ? paymentPayload.optJSONObject("payment") : null;
        JSONObject paymentEntity = paymentData != null ? paymentData.optJSONObject("entity") : null;

        if (paymentEntity == null) {
            return;
        }

        String paymentId = paymentEntity.optString("id", "");
        String orderId = paymentEntity.optString("order_id", "");

        if (paymentId.isBlank() || orderId.isBlank()) {
            return;
        }

        Optional<PaymentTransaction> existing = paymentTransactions.findByRazorpayOrderId(orderId);
        if (existing.isEmpty()) {
            existing = paymentTransactions.findByRazorpayPaymentId(paymentId);
        }

        PaymentTransaction paymentTransaction = existing.orElseGet(PaymentTransaction::new);
        if (paymentTransaction.getId() != null && PaymentTransactionStatus.VERIFIED.equals(paymentTransaction.getStatus())
                && ("payment.captured".equalsIgnoreCase(event) || "payment.authorized".equalsIgnoreCase(event))) {
            return;
        }

        paymentTransaction.setRazorpayOrderId(orderId);
        paymentTransaction.setRazorpayPaymentId(paymentId);
        paymentTransaction.setWebhookEvent(event);

        if ("payment.captured".equalsIgnoreCase(event) || "payment.authorized".equalsIgnoreCase(event)) {
            if (paymentTransaction.getCompany() != null && paymentTransaction.getSubscription() != null) {
                Subscription subscription = paymentTransaction.getSubscription();
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                subscriptions.save(subscription);
            }
            paymentTransaction.setStatus(PaymentTransactionStatus.VERIFIED);
            paymentTransaction.setPaidAt(LocalDateTime.now());
            paymentTransactions.save(paymentTransaction);
        } else if ("payment.failed".equalsIgnoreCase(event)) {
            paymentTransaction.setStatus(PaymentTransactionStatus.FAILED);
            paymentTransaction.setPaidAt(LocalDateTime.now());
            paymentTransactions.save(paymentTransaction);
        }
    }

    public LoginResponse loginAfterPayment(Long userId, String password) {
        User user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found."));
        LoginRequest login = new LoginRequest();
        login.setUsername(user.getUsername());
        login.setPassword(password);
        return authService.login(login, null);
    }

    private String generateSignature(String payload) {
        return generateSignature(payload, razorpayKeySecret);
    }

    private String generateSignature(String payload, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new BadRequestException("Razorpay is not configured yet.");
        }
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new BadRequestException("Unable to verify Razorpay signature.");
        }
    }

    private BigDecimal calculateSubscriptionAmount(String plan, Integer employeeCount, String billingCycle) {
        BigDecimal perEmployeeRate = BillingPricing.rateFor(plan, billingCycle);
        return perEmployeeRate.multiply(BigDecimal.valueOf(BillingPricing.normalizeEmployeeCount(employeeCount)))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal calculateVerificationAmount() {
        return new BigDecimal("1.00");
    }

    private void notifyPaymentFailure(User user, Company company, String orderId) {
        emailService.sendPaymentFailureEmail(user.getEmail(), user.getFullName(), company.getName(), orderId);
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private record PlanSpec(Plan plan, String label, Integer employeeLimit, Integer deviceLimit) {
    }
}
