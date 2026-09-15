package com.haodaone.auth.service;

import com.haodaone.auth.dto.LoginRequest;
import com.haodaone.auth.dto.LoginResponse;
import com.haodaone.auth.dto.RegisterRequest;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.entity.Plan;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.entity.SubscriptionStatus;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.repository.SubscriptionRepository;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public RegistrationService(UserRepository users, CompanyRepository companies, RoleRepository roles,
                               SubscriptionRepository subscriptions, PasswordEncoder passwordEncoder,
                               AuthService authService) {
        this.users = users;
        this.companies = companies;
        this.roles = roles;
        this.subscriptions = subscriptions;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @Transactional
    public LoginResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
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
        user.setAccountStatus("ACTIVE");
        user.setCompany(savedCompany);
        user.setRoles(new HashSet<>(List.of(companyAdmin)));
        users.save(user);

        LocalDate trialStart = LocalDate.now();
        Subscription subscription = new Subscription();
        subscription.setCompany(savedCompany);
        subscription.setPlan(Plan.TRIAL);
        subscription.setStatus(SubscriptionStatus.TRIALING);
        subscription.setEmployeeLimit(25);
        subscription.setStartDate(trialStart);
        subscription.setRenewalDate(trialStart.plusDays(14));
        subscriptions.save(subscription);

        LoginRequest login = new LoginRequest();
        login.setUsername(email);
        login.setPassword(request.password());
        return authService.login(login, httpRequest);
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
}
