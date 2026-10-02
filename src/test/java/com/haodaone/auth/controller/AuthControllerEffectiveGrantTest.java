package com.haodaone.auth.controller;

import com.haodaone.auth.service.AuthService;
import com.haodaone.auth.service.PasswordResetService;
import com.haodaone.auth.service.RegistrationService;
import com.haodaone.auth.service.VerificationService;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.User;
import com.haodaone.user.entity.UserPermissionGrant;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerEffectiveGrantTest {

    @Test
    void meReturnsActiveGrantPermissionsAndTheirStoredScopes() {
        Company company = new Company();
        company.setId(7L);
        User user = new User();
        user.setId(12L);
        user.setUsername("employee");
        user.setEmail("employee@example.test");
        user.setCompany(company);

        Permission permission = new Permission();
        permission.setCode("MONITORING_VIEW");
        UserPermissionGrant grant = new UserPermissionGrant();
        grant.setCompany(company);
        grant.setUser(user);
        grant.setPermission(permission);
        grant.setScope(PermissionScope.TEAM);
        grant.setGrantedAt(LocalDateTime.now());

        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        when(employeeRepository.findByUser_UsernameAndDeletedFalse("employee"))
                .thenReturn(java.util.Optional.empty());
        when(employeeRepository.findByEmailIgnoreCaseAndDeletedFalse("employee@example.test"))
                .thenReturn(java.util.Optional.empty());
        AuthController controller = new AuthController(
                mock(AuthService.class), mock(RegistrationService.class), mock(VerificationService.class),
                mock(PasswordResetService.class), employeeRepository);

        var response = controller.me(new CustomUserPrincipal(user, List.of(grant)));

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().getPermissions().contains("MONITORING_VIEW"));
        assertEquals(java.util.Set.of(PermissionScope.TEAM),
                response.getBody().getScopes().get("MONITORING_VIEW"));
    }
}
