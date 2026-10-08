package com.haodaone.assistant;

import com.haodaone.assistant.dto.AssistantChatRequest;
import com.haodaone.assistant.dto.AssistantChatResponse;
import com.haodaone.company.entity.Company;
import com.haodaone.document.dto.EmployeeDocumentDTO;
import com.haodaone.document.service.EmployeeDocumentService;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.dto.LeaveBalanceDTO;
import com.haodaone.leave.service.LeaveRequestService;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {
    @Mock
    private AssistantLanguageProvider languageProvider;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private LeaveRequestService leaveRequestService;

    @Mock
    private EmployeeDocumentService employeeDocumentService;

    private AssistantService assistantService;

    @BeforeEach
    void setUp() {
        assistantService = new AssistantService(
                languageProvider, employeeRepository, leaveRequestService, employeeDocumentService);
        TenantContext.setCurrentTenant(42L);
        User user = new User();
        user.setId(7L);
        user.setUsername("employee@example.test");
        lenient().when(employeeRepository.findByUser_IdAndDeletedFalse(7L)).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserPrincipal(user),
                null,
                List.of(
                        new SimpleGrantedAuthority("SELF_PROFILE_VIEW"),
                        new SimpleGrantedAuthority("SELF_LEAVE_VIEW")
                )
        ));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void mapsNaturalLeaveRequestToRealLeavePage() {
        useLocalResolver();
        var response = assistantService.chat(new AssistantChatRequest("guide me to apply leaves", null, List.of()));

        assertThat(response.intent()).isEqualTo("LEAVE_GUIDE");
        assertThat(response.mode()).isEqualTo("GUIDE");
        assertThat(response.actions()).singleElement()
                .extracting(AssistantChatResponse.AssistantAction::route)
                .isEqualTo("/my-profile?tab=leave");
        assertThat(response.requiresConfirmation()).isFalse();
    }

    @Test
    void unavailablePermissionDoesNotExposeLeaveBalanceTool() {
        useLocalResolver();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserPrincipal(userWithId(7L)),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        ));

        var response = assistantService.chat(new AssistantChatRequest("What is my leave balance?", null, List.of()));

        assertThat(response.intent()).isEqualTo("UNKNOWN");
        verify(leaveRequestService, never()).getBalances(org.mockito.ArgumentMatchers.anyLong(), anyInt());
    }

    @Test
    void returnsOnlyCurrentEmployeesLeaveBalance() {
        useLocalResolver();
        Employee employee = new Employee();
        employee.setId(51L);
        Company company = new Company();
        company.setId(42L);
        employee.setCompany(company);
        when(employeeRepository.findByUser_IdAndDeletedFalse(7L)).thenReturn(Optional.of(employee));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserPrincipal(userWithId(7L)),
                null,
                List.of(
                        new SimpleGrantedAuthority("SELF_PROFILE_VIEW"),
                        new SimpleGrantedAuthority("SELF_LEAVE_VIEW")
                )
        ));
        when(leaveRequestService.getBalances(org.mockito.ArgumentMatchers.eq(51L), anyInt()))
                .thenReturn(List.of(new LeaveBalanceDTO(3L, "Casual Leave", 2026, 12, 0, 4)));

        var response = assistantService.chat(new AssistantChatRequest("What is my leave balance?", null, List.of()));

        assertThat(response.intent()).isEqualTo("MY_LEAVE_BALANCE");
        assertThat(response.message()).contains("Casual Leave: 8.0 days remaining");
        verify(leaveRequestService).getBalances(org.mockito.ArgumentMatchers.eq(51L), anyInt());
    }

    @Test
    void resolvesLeaveBalanceFollowUpFromConversationContext() {
        useLocalResolver();
        Employee employee = new Employee();
        employee.setId(51L);
        Company company = new Company();
        company.setId(42L);
        employee.setCompany(company);
        when(employeeRepository.findByUser_IdAndDeletedFalse(7L)).thenReturn(Optional.of(employee));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserPrincipal(userWithId(7L)),
                null,
                List.of(
                        new SimpleGrantedAuthority("SELF_PROFILE_VIEW"),
                        new SimpleGrantedAuthority("SELF_LEAVE_VIEW")
                )
        ));
        when(leaveRequestService.getBalances(org.mockito.ArgumentMatchers.eq(51L), anyInt()))
                .thenReturn(List.of(new LeaveBalanceDTO(3L, "Casual Leave", 2026, 12, 0, 4)));
        var history = List.of(
                new AssistantChatRequest.ConversationTurn("user", "What is my leave balance?"),
                new AssistantChatRequest.ConversationTurn("assistant", "You have 8 days remaining.")
        );

        var response = assistantService.chat(new AssistantChatRequest("Can I use it tomorrow?", null, history));

        assertThat(response.intent()).isEqualTo("MY_LEAVE_BALANCE");
        assertThat(response.message()).contains("Casual Leave: 8.0 days remaining");
    }

    @Test
    void returnsOnlyCurrentEmployeesDocumentStatusSummary() {
        useLocalResolver();
        Employee employee = new Employee();
        employee.setId(51L);
        Company company = new Company();
        company.setId(42L);
        employee.setCompany(company);
        when(employeeRepository.findByUser_IdAndDeletedFalse(7L)).thenReturn(Optional.of(employee));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserPrincipal(userWithId(7L)),
                null,
                List.of(
                        new SimpleGrantedAuthority("SELF_PROFILE_VIEW"),
                        new SimpleGrantedAuthority("SELF_DOCUMENT_VIEW")
                )
        ));
        EmployeeDocumentDTO pending = org.mockito.Mockito.mock(EmployeeDocumentDTO.class);
        org.mockito.Mockito.when(pending.getStatus()).thenReturn("PENDING");
        org.mockito.Mockito.when(pending.getExpiryDate()).thenReturn(null);
        when(employeeDocumentService.byEmployee(51L)).thenReturn(List.of(pending));

        var response = assistantService.chat(new AssistantChatRequest("What is my document status?", null, List.of()));

        assertThat(response.intent()).isEqualTo("MY_DOCUMENTS");
        assertThat(response.message()).contains("1 document", "1 pending review");
        assertThat(response.actions()).singleElement()
                .extracting(AssistantChatResponse.AssistantAction::route)
                .isEqualTo("/my-profile?tab=documents");
        verify(employeeDocumentService).byEmployee(51L);
    }

    @Test
    void doesNotQueryDocumentsWithoutCurrentTenantEmployee() {
        useLocalResolver();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserPrincipal(userWithId(7L)),
                null,
                List.of(
                        new SimpleGrantedAuthority("SELF_PROFILE_VIEW"),
                        new SimpleGrantedAuthority("SELF_DOCUMENT_VIEW")
                )
        ));

        var response = assistantService.chat(new AssistantChatRequest("Show my documents", null, List.of()));

        assertThat(response.intent()).isEqualTo("UNKNOWN");
        verify(employeeDocumentService, never()).byEmployee(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void refusesToRunWithoutResolvedTenant() {
        TenantContext.clear();

        assertThatThrownBy(() -> assistantService.chat(
                new AssistantChatRequest("How do I apply leave?", null, List.of())
        )).isInstanceOf(AccessDeniedException.class);
    }

    private User userWithId(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("employee@example.test");
        return user;
    }

    private void useLocalResolver() {
        when(languageProvider.classify(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(Optional.empty());
    }
}
