package com.haodaone.auth.service;

import com.haodaone.auth.dto.LoginRequest;
import com.haodaone.auth.dto.LoginResponse;
import com.haodaone.auth.dto.RegisterRequest;
import com.haodaone.auth.dto.SignupRegistrationResponse;
import com.haodaone.billing.service.BillingPricing;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.entity.Plan;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.entity.SubscriptionStatus;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.repository.SubscriptionRepository;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.auth.service.VerificationService;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

@Service
public class RegistrationService {

    private final UserRepository users;
    private final CompanyRepository companies;
    private final RoleRepository roles;
    private final SubscriptionRepository subscriptions;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final EmailService emailService;
    private final VerificationService verificationService;

    public RegistrationService(UserRepository users, CompanyRepository companies, RoleRepository roles,
                               SubscriptionRepository subscriptions, PasswordEncoder passwordEncoder,
                               AuthService authService, EmailService emailService,
                               VerificationService verificationService) {
        this.users = users;
        this.companies = companies;
        this.roles = roles;
        this.subscriptions = subscriptions;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.emailService = emailService;
        this.verificationService = verificationService;
    }

    @Transactional
    public Object register(RegisterRequest request, HttpServletRequest httpRequest) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new BadRequestException("This email is already registered. Try logging in instead.");
        }
        if (companies.findByNameIgnoreCase(request.organizationName().trim()).filter(company -> !company.isDeleted()).isPresent()) {
            throw new BadRequestException("That organization name is already in use.");
        }
        if (!request.password().matches(".*[A-Z].*") || !request.password().matches(".*[0-9].*")) {
            throw new BadRequestException("Choose a stronger password.");
        }

        Role companyAdmin = roles.findByName("COMPANY_ADMIN")
                .orElseThrow(() -> new BadRequestException("Registration is temporarily unavailable. Please try again later."));

        Company company = new Company();
        company.setName(request.organizationName().trim());
        company.setIndustry(request.industry().trim());
        company.setCompanySize(request.companySize().trim());
        company.setCountry(request.country().trim());
        company.setWorkspaceInterests(normalizeInterests(request.interests()));
        Company savedCompany = companies.save(company);

        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName(request.firstName().trim() + " " + request.lastName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);
        user.setMustChangePassword(false);
        user.setCompany(savedCompany);
        user.setRoles(new HashSet<>(List.of(companyAdmin)));

        String planName = BillingPricing.resolveSignupPlan(request.plan());
        if (BillingPricing.isExplicitPaidPlan(planName) || BillingPricing.TRIAL.equals(planName)) {
            String subscriptionPlanName = BillingPricing.TRIAL.equals(planName)
                    ? BillingPricing.STARTER
                    : planName;
            user.setAccountStatus("PENDING_PAYMENT");
            User savedUser = users.save(user);

            String billingCycle = BillingPricing.normalizeBillingCycle(request.billingCycle());
            int employeeCount = BillingPricing.normalizeEmployeeCount(request.employeeCount());
            java.math.BigDecimal amount = calculateAmount(subscriptionPlanName, employeeCount, billingCycle);

            Subscription subscription = new Subscription();
            subscription.setCompany(savedCompany);
            subscription.setPlan(Plan.valueOf(subscriptionPlanName));
            subscription.setStatus(SubscriptionStatus.PENDING_PAYMENT);
            subscription.setEmployeeLimit(Math.max(1, employeeCount));
            subscription.setDeviceLimit(Math.max(1, Math.min(100, employeeCount)));
            subscription.setBillingCycle(billingCycle);
            subscription.setBillableEmployeeCount(employeeCount);
            subscription.setStartDate(LocalDate.now());
            subscription.setRenewalDate(LocalDate.now().plusDays(30));
            subscription.setRate(BillingPricing.rateFor(subscriptionPlanName, billingCycle));
            subscription.setAmount(amount);
            subscriptions.save(subscription);

            String verificationToken = verificationService.createToken(savedUser);
            afterCommit(() -> emailService.sendVerificationEmail(savedUser.getEmail(), savedUser.getFullName(), verificationToken));

            return new SignupRegistrationResponse(true, savedCompany.getId(), savedUser.getId(), subscriptionPlanName, "INR", new java.math.BigDecimal("1.00"), "A ₹1 verification is required to start your free trial.");
        }

        user.setAccountStatus("ACTIVE");
        User savedUser = users.saveAndFlush(user);

        LocalDate trialStart = LocalDate.now();
        Subscription subscription = new Subscription();
        subscription.setCompany(savedCompany);
        subscription.setPlan(Plan.TRIAL);
        subscription.setStatus(SubscriptionStatus.TRIALING);
        subscription.setEmployeeLimit(25);
        subscription.setAmount(java.math.BigDecimal.ZERO);
        subscription.setRate(java.math.BigDecimal.ZERO);
        subscription.setStartDate(trialStart);
        subscription.setRenewalDate(trialStart.plusDays(14));
        subscriptions.save(subscription);

        String verificationToken = verificationService.createToken(savedUser);

        String customerName = savedUser.getFullName();
        String customerEmail = savedUser.getEmail();
        String organizationName = savedCompany.getName();
        String trialStartValue = subscription.getStartDate().toString();
        String trialEndValue = subscription.getRenewalDate().toString();
        afterCommit(() -> {
            emailService.sendWelcomeEmail(customerEmail, customerName, organizationName,
                subscription.getPlan().name(), subscription.getEmployeeLimit(), subscription.getBillingCycle(),
                trialStartValue, trialEndValue);
            emailService.sendVerificationEmail(customerEmail, customerName, verificationToken);
        });

        LoginRequest login = new LoginRequest();
        login.setUsername(email);
        login.setPassword(request.password());
        return authService.login(login, httpRequest);
    }

    private java.math.BigDecimal calculateAmount(String plan, int employeeCount, String billingCycle) {
        return BillingPricing.rateFor(plan, billingCycle)
                .multiply(java.math.BigDecimal.valueOf(employeeCount))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String normalizeInterests(List<String> interests) {
        if (interests == null) return "";
        return interests.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().replace(",", ""))
                .distinct()
                .limit(20)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
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
}
