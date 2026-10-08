package com.haodaone.notifications.service;

import com.haodaone.company.entity.Company;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private EmailService emailService;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setId(7L);
        User user = new User();
        user.setId(12L);
        user.setCompany(company);
        CustomUserPrincipal principal = new CustomUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        TenantContext.setCurrentTenant(7L);
        service = new NotificationService(jdbcTemplate, emailService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void inboxClampsPaginationAndScopesEveryQueryToTheAuthenticatedUserAndTenant() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);

        Map<String, Object> result = service.inbox(-1, 500, "leave_decision");

        assertEquals(0, result.get("page"));
        assertEquals(100, result.get("size"));
        assertEquals(0L, result.get("unreadCount"));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).queryForList(sql.capture(), any(Object[].class));
        assertTrue(sql.getAllValues().get(0).contains("company_id = ? AND recipient_user_id = ?"));
    }

    @Test
    void markReadIncludesNotificationTenantAndRecipientInTheUpdatePredicate() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        service.markRead(55L);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), args.capture());
        assertArrayEquals(new Object[]{55L, 7L, 12L}, args.getValue());
    }

    @Test
    void rejectsACompanyContextThatDoesNotBelongToTheAuthenticatedUser() {
        TenantContext.setCurrentTenant(99L);

        assertThrows(AccessDeniedException.class, service::unreadCount);
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test
    void scopedApproverResolutionExcludesCustomAndUsesActualTeamAndDepartmentMembership() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("role_permission_scope")) return List.of();
            if (sql.contains("user_permission_grant")) return List.of();
            return List.of();
        });

        service.notifyPermissionRecipients(7L, 32L, "LEAVE_APPROVE", "LEAVE_SUBMITTED",
                "Leave request needs review", "A leave request is waiting for review.",
                "LeaveRequest", 88L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).queryForList(sql.capture(), any(Object[].class));
        assertTrue(sql.getAllValues().get(0).contains("rps.scope <> 'CUSTOM'"));
        assertTrue(sql.getAllValues().get(0).contains("recipient.team_id = target.team_id"));
        assertTrue(sql.getAllValues().get(0).contains("recipient.department_id = target.department_id"));
        assertTrue(sql.getAllValues().get(1).contains("g.scope <> 'CUSTOM'"));
    }
}
