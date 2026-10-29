package com.haodaone.billing.controller;

import com.haodaone.company.entity.Company;
import com.haodaone.company.entity.Plan;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.entity.SubscriptionStatus;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.repository.SubscriptionRepository;
import com.haodaone.security.JwtService;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/billing/status powers the subscription banner for every signed-in user
 * (SubscriptionBillingController -> BillingAccountService.status). Production logged
 * NoResourceFoundException for this path, i.e. the running build had no such mapping.
 * These tests pin the mapping, its auth rules, and the 404 behaviour for unknown paths.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingStatusEndpointTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    @Test
    void nonAdminEmployeeGetsTheirCompanysSubscriptionStatus() throws Exception {
        Company company = company();
        Subscription subscription = new Subscription();
        subscription.setCompany(company);
        subscription.setPlan(Plan.STARTER);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingCycle("MONTHLY");
        subscription.setStartDate(LocalDate.now().minusDays(5));
        subscription.setRenewalDate(LocalDate.now().plusDays(25));
        subscriptionRepository.save(subscription);

        // Same role as the user in the production screenshot.
        String token = tokenFor(company, "IT_ADMINISTRATOR");

        mockMvc.perform(get("/api/billing/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.plan").value("STARTER"))
                .andExpect(jsonPath("$.readOnly").value(false));
    }

    @Test
    void companyWithoutSubscriptionReportsNone() throws Exception {
        String token = tokenFor(company(), "IT_ADMINISTRATOR");

        mockMvc.perform(get("/api/billing/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NONE"));
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/billing/status"))
                .andExpect(result -> {
                    int code = result.getResponse().getStatus();
                    if (code != 401 && code != 403) {
                        throw new AssertionError("expected 401/403 for an anonymous request but got " + code);
                    }
                });
    }

    @Test
    void unknownApiPathIsNotFoundInsteadOfServerError() throws Exception {
        String token = tokenFor(company(), "IT_ADMINISTRATOR");

        mockMvc.perform(get("/api/billing/no-such-endpoint").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private Company company() {
        Company company = new Company();
        company.setName("Billing status " + UUID.randomUUID());
        return companyRepository.save(company);
    }

    private String tokenFor(Company company, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(roleName + " role is not seeded"));
        String username = "billing-status-" + UUID.randomUUID() + "@example.test";
        User user = new User();
        user.setUsername(username);
        user.setEmail(username);
        user.setFullName("Billing Status");
        user.setPasswordHash(passwordEncoder.encode("Password1!"));
        user.setCompany(company);
        user.setActive(true);
        user.setAccountStatus("ACTIVE");
        user.setRoles(Set.of(role));
        userRepository.save(user);
        return jwtService.generateAccessToken(username, List.of(roleName));
    }
}
