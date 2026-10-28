package com.haodaone.billing.enforcement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.company.entity.Company;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.entity.SubscriptionStatus;
import com.haodaone.company.repository.SubscriptionRepository;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionEnforcementInterceptorTest {

    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final SubscriptionEnforcementInterceptor interceptor = new SubscriptionEnforcementInterceptor(subscriptions, new ObjectMapper().findAndRegisterModules());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void signIn(String roleName, SubscriptionStatus status) {
        Company company = new Company();
        company.setId(5L);
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(9L);
        user.setCompany(company);
        user.setRoles(Set.of(role));
        CustomUserPrincipal principal = new CustomUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        Subscription sub = new Subscription();
        sub.setCompany(company);
        sub.setStatus(status);
        when(subscriptions.findByCompany_IdAndDeletedFalse(5L)).thenReturn(Optional.of(sub));
    }

    private MockHttpServletResponse call(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean proceed = interceptor.preHandle(request, response, new Object());
        if (proceed) response.setStatus(200);
        return response;
    }

    @Test
    void suspendedCompanyGets402OnWritesButCanStillRead() throws Exception {
        signIn("HR_ADMIN", SubscriptionStatus.SUSPENDED);
        MockHttpServletResponse post = call("POST", "/api/employees");
        assertThat(post.getStatus()).isEqualTo(402);
        assertThat(post.getContentAsString()).contains("SUBSCRIPTION_INACTIVE");
        assertThat(call("PUT", "/api/leave-requests/3").getStatus()).isEqualTo(402);
        assertThat(call("GET", "/api/employees").getStatus()).isEqualTo(200);
    }

    @Test
    void billingAuthAndExpiredTrialRules() throws Exception {
        signIn("COMPANY_ADMIN", SubscriptionStatus.EXPIRED);
        assertThat(call("POST", "/api/billing/subscription/pay-now").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/logout").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/mfa/verify").getStatus()).isEqualTo(200);
        assertThat(call("DELETE", "/api/departments/1").getStatus()).isEqualTo(402);
    }

    @Test
    void activeAndPastDueCompaniesAndSuperAdminAreNotBlocked() throws Exception {
        signIn("HR_ADMIN", SubscriptionStatus.PAST_DUE);
        assertThat(call("POST", "/api/employees").getStatus()).isEqualTo(200);
        SecurityContextHolder.clearContext();
        signIn("SUPER_ADMIN", SubscriptionStatus.SUSPENDED);
        assertThat(call("POST", "/api/employees").getStatus()).isEqualTo(200);
    }
}
