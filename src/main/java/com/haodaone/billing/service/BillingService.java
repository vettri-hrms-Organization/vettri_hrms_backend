package com.haodaone.billing.service;

import com.haodaone.auth.dto.LoginRequest;
import com.haodaone.auth.dto.LoginResponse;
import com.haodaone.auth.dto.RegisterRequest;
import com.haodaone.auth.dto.SignupRegistrationResponse;
import com.haodaone.auth.service.AuthService;
import com.haodaone.billing.dto.PlanPricingResponse;
import com.haodaone.company.entity.Company;
import com.haodaone.company.entity.Plan;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.entity.SubscriptionStatus;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.repository.SubscriptionRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class BillingService {

    private static final Map<String, PlanSpec> PLAN_PRICING = Map.of(
            "STARTER", new PlanSpec(Plan.STARTER, "Starter", new BigDecimal("2999"), 25, 2),
            "BUSINESS", new PlanSpec(Plan.BUSINESS, "Business", new BigDecimal("5999"), 100, 10),
            "ENTERPRISE", new PlanSpec(Plan.ENTERPRISE, "Enterprise", new BigDecimal("14999"), 500, 50)
    );

    private final UserRepository users;
    private final CompanyRepository companies;
    private final SubscriptionRepository subscriptions;
    private final AuthService authService;

    @Value("${app.razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${app.razorpay.key-secret:}")
    private String razorpayKeySecret;

    @Value("${app.razorpay.currency:INR}")
    private String razorpayCurrency;

    public BillingService(UserRepository users, CompanyRepository companies,
                          SubscriptionRepository subscriptions, AuthService authService) {
        this.users = users;
        this.companies = companies;
        this.subscriptions = subscriptions;
        this.authService = authService;
    }

    public List<PlanPricingResponse> pricing() {
        return PLAN_PRICING.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new PlanPricingResponse(
                        entry.getKey(),
                        entry.getValue().label(),
                        entry.getValue().amount(),
                        razorpayCurrency,
                        entry.getValue().employeeLimit(),
                        entry.getValue().deviceLimit(),
                        "Plan for " + entry.getValue().label() + " companies"
                )).toList();
    }

    @Transactional
    public Map<String, Object> createOrder(Long companyId, Long userId, String plan) {
        if (companyId == null || userId == null) {
            throw new BadRequestException("A company and user are required for checkout.");
        }

        String normalizedPlan = normalizePlan(plan);
        PlanSpec spec = PLAN_PRICING.get(normalizedPlan);
        if (spec == null) {
            throw new BadRequestException("Plan not found.");
        }

        Company company = companies.findById(companyId)
                .orElseThrow(() -> new BadRequestException("Company not found."));
        User user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found."));

        if (razorpayKeyId == null || razorpayKeyId.isBlank() || razorpayKeySecret == null || razorpayKeySecret.isBlank()) {
            throw new BadRequestException("Payment is temporarily unavailable. Add Razorpay credentials to the backend environment.");
        }

        try {
            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", spec.amount().multiply(new BigDecimal("100")).intValueExact());
            orderRequest.put("currency", razorpayCurrency);
            orderRequest.put("receipt", "vettri-" + company.getId() + "-" + System.currentTimeMillis());
            orderRequest.put("notes", new JSONObject(Map.of(
                    "companyId", String.valueOf(company.getId()),
                    "userId", String.valueOf(user.getId()),
                    "plan", normalizedPlan,
                    "organizationName", company.getName(),
                    "customerEmail", user.getEmail()
            )));

            Order order = client.orders.create(orderRequest);
            return Map.of(
                    "key", razorpayKeyId,
                    "orderId", order.get("id"),
                    "amount", order.get("amount"),
                    "currency", order.get("currency"),
                    "plan", normalizedPlan,
                    "companyId", company.getId(),
                    "userId", user.getId(),
                    "organizationName", company.getName(),
                    "customerName", user.getFullName(),
                    "customerEmail", user.getEmail()
            );
        } catch (RazorpayException ex) {
            throw new BadRequestException("Unable to create a Razorpay order: " + ex.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> verifyPayment(Long companyId, Long userId, String plan, String razorpayPaymentId, String razorpayOrderId, String razorpaySignature) {
        if (companyId == null || userId == null) {
            throw new BadRequestException("Payment verification is missing a user or company.");
        }

        String normalizedPlan = normalizePlan(plan);
        if (normalizedPlan == null) {
            throw new BadRequestException("Plan not found.");
        }

        Company company = companies.findById(companyId)
                .orElseThrow(() -> new BadRequestException("Company not found."));
        User user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found."));

        if (razorpayPaymentId == null || razorpayOrderId == null || razorpaySignature == null || razorpaySignature.isBlank()) {
            throw new BadRequestException("Payment verification failed. Missing Razorpay data.");
        }

        String expectedSignature = generateSignature(razorpayOrderId + "|" + razorpayPaymentId);
        if (!expectedSignature.equalsIgnoreCase(razorpaySignature)) {
            throw new BadRequestException("Payment verification failed. Signature mismatch.");
        }

        Optional<Subscription> existing = subscriptions.findByCompany_IdAndDeletedFalse(companyId);
        if (existing.isPresent()) {
            Subscription sub = existing.get();
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setPlan(Plan.valueOf(normalizedPlan));
            sub.setAmount(PLAN_PRICING.get(normalizedPlan).amount());
            sub.setStartDate(LocalDate.now());
            sub.setRenewalDate(LocalDate.now().plusDays(30));
            subscriptions.save(sub);
        }

        user.setAccountStatus("ACTIVE");
        user.setActive(true);
        users.save(user);

        return Map.of(
                "status", "verified",
                "companyId", company.getId(),
                "userId", user.getId(),
                "plan", normalizedPlan,
                "message", "Payment verified and company activated.");
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
        if (razorpayKeySecret == null || razorpayKeySecret.isBlank()) {
            throw new BadRequestException("Razorpay is not configured yet.");
        }
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(razorpayKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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

    private String normalizePlan(String plan) {
        if (plan == null) return null;
        String normalized = plan.trim().toUpperCase(Locale.ROOT);
        return PLAN_PRICING.containsKey(normalized) ? normalized : null;
    }

    private record PlanSpec(Plan plan, String label, BigDecimal amount, Integer employeeLimit, Integer deviceLimit) {
    }
}
