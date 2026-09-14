package com.haodaone.employee.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.ConflictException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmployeeInvitation;
import com.haodaone.employee.repository.EmployeeInvitationRepository;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeInvitationServiceTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock EmployeeInvitationRepository invitationRepository;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;
    @Mock AuditLogService auditLogService;

    private EmployeeInvitationService service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new EmployeeInvitationService(employeeRepository, invitationRepository, userRepository,
                roleRepository, passwordEncoder, emailService, auditLogService);
        employee = new Employee();
        employee.setId(10L);
        employee.setEmployeeCode("EMP0010");
        employee.setFirstName("A");
        employee.setLastName("Vikkash");
        employee.setEmail("  AMVIKKASH@GMAIL.COM ");
        TenantContext.setCurrentTenant(1L);
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(employee));
        when(emailService.sendEmployeeInvitationEmail(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationRepository.save(any(EmployeeInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createsNewUserWithNormalizedEmail() {
        Role role = new Role();
        when(userRepository.findByEmailIgnoreCase("amvikkash@gmail.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(role));

        service.sendInvitation(10L);

        assertEquals("amvikkash@gmail.com", employee.getEmail());
        verify(userRepository).lockEmployeeForCurrentTransaction(10L);
        verify(userRepository).save(any(User.class));
        verify(employeeRepository).save(employee);
    }

    @Test
    void reusesMatchingLinkedUser() {
        User user = pendingUser(20L, "amvikkash@gmail.com");
        employee.setUser(user);
        when(userRepository.findByEmailIgnoreCase("amvikkash@gmail.com")).thenReturn(Optional.of(user));

        service.sendInvitation(10L);

        verify(userRepository).save(user);
        verify(invitationRepository).save(any(EmployeeInvitation.class));
        verify(roleRepository, never()).findByName(anyString());
    }

    @Test
    void rejectsEmailOwnedByAnotherEmployee() {
        User user = pendingUser(20L, "amvikkash@gmail.com");
        Employee otherEmployee = new Employee();
        otherEmployee.setId(11L);
        when(userRepository.findByEmailIgnoreCase("amvikkash@gmail.com")).thenReturn(Optional.of(user));
        when(employeeRepository.findByUser_IdAndDeletedFalse(20L)).thenReturn(Optional.of(otherEmployee));

        assertThrows(ConflictException.class, () -> service.sendInvitation(10L));

        verify(userRepository, never()).save(any(User.class));
        verify(invitationRepository, never()).save(any(EmployeeInvitation.class));
    }

    @Test
    void rejectsAlreadyActivatedAccount() {
        User user = pendingUser(20L, "amvikkash@gmail.com");
        user.setActive(true);
        user.setMustChangePassword(false);
        user.setAccountStatus("ACTIVE");
        employee.setUser(user);
        when(userRepository.findByEmailIgnoreCase("amvikkash@gmail.com")).thenReturn(Optional.of(user));

        assertThrows(ConflictException.class, () -> service.sendInvitation(10L));
    }

    @Test
    void rejectsNormalInvitationWhenOneIsPending() {
        User user = pendingUser(20L, "amvikkash@gmail.com");
        employee.setUser(user);
        EmployeeInvitation previous = new EmployeeInvitation();
        previous.setStatus("PENDING");
        when(userRepository.findByEmailIgnoreCase("amvikkash@gmail.com")).thenReturn(Optional.of(user));
        when(invitationRepository.findByEmployee_IdAndStatus(10L, "PENDING")).thenReturn(Optional.of(previous));

        assertThrows(ConflictException.class, () -> service.sendInvitation(10L));

        assertEquals("PENDING", previous.getStatus());
        assertNull(previous.getUsedAt());
        verify(invitationRepository, never()).save(any(EmployeeInvitation.class));
    }

    @Test
    void replacesPreviousPendingInvitationOnResend() {
        User user = pendingUser(20L, "amvikkash@gmail.com");
        employee.setUser(user);
        EmployeeInvitation previous = new EmployeeInvitation();
        previous.setStatus("PENDING");
        when(userRepository.findByEmailIgnoreCase("amvikkash@gmail.com")).thenReturn(Optional.of(user));
        when(invitationRepository.findByEmployee_IdAndStatus(10L, "PENDING")).thenReturn(Optional.of(previous));

        service.resendInvitation(10L);

        assertEquals("REVOKED", previous.getStatus());
        verify(invitationRepository, times(2)).save(any(EmployeeInvitation.class));
    }

    private User pendingUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setAccountStatus("INVITED");
        user.setActive(false);
        user.setMustChangePassword(true);
        return user;
    }
}