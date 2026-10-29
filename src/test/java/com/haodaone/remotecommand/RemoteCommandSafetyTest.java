package com.haodaone.remotecommand;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.remotecommand.dto.RemoteCommandDTO;
import com.haodaone.remotecommand.entity.RemoteCommandJob;
import com.haodaone.remotecommand.repository.RemoteCommandJobRepository;
import com.haodaone.remotecommand.service.RemoteCommandService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Approved actions queue freely; raw scripts need REMOTE_SCRIPT_RUN plus the admin's password. */
class RemoteCommandSafetyTest {

    private final RemoteCommandJobRepository jobs = mock(RemoteCommandJobRepository.class);
    private final MonitoredDeviceRepository devices = mock(MonitoredDeviceRepository.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final RemoteCommandService service = new RemoteCommandService(jobs, devices, audit, authorization, users, passwords);

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(7L);
        Company company = new Company();
        company.setId(7L);
        MonitoredDevice device = new MonitoredDevice();
        device.setId(3L);
        device.setDeviceName("Laptop");
        device.setCompany(company);
        when(devices.findByIdAndCompany_IdAndDeletedFalse(3L, 7L)).thenReturn(Optional.of(device));
        when(jobs.save(any(RemoteCommandJob.class))).thenAnswer(invocation -> {
            RemoteCommandJob job = invocation.getArgument(0);
            job.setId(11L);
            return job;
        });
        User admin = new User();
        admin.setPasswordHash("hash");
        when(users.findById(5L)).thenReturn(Optional.of(admin));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void approvedActionIsQueuedWithItsOwnTimeout() {
        RemoteCommandDTO.Response response = service.create(3L, new RemoteCommandDTO.CreateRequest("flush_dns", "action"), 5L);
        assertEquals("FLUSH_DNS", response.command());
        assertEquals("ACTION", response.shellType());
        assertEquals("Flush DNS cache", response.actionLabel());
        verify(authorization, never()).hasOrganizationScope(anyString());
    }

    @Test
    void unknownActionIsRejected() {
        assertThrows(BadRequestException.class,
                () -> service.create(3L, new RemoteCommandDTO.CreateRequest("FORMAT_DISK", "ACTION"), 5L));
        verify(jobs, never()).save(any());
    }

    @Test
    void rawScriptWithoutPermissionIsDenied() {
        when(authorization.hasOrganizationScope("REMOTE_SCRIPT_RUN")).thenReturn(false);
        assertThrows(AccessDeniedException.class,
                () -> service.create(3L, new RemoteCommandDTO.CreateRequest("whoami", "CMD", "secret"), 5L));
        verify(jobs, never()).save(any());
    }

    @Test
    void rawScriptNeedsTheCorrectPassword() {
        when(authorization.hasOrganizationScope("REMOTE_SCRIPT_RUN")).thenReturn(true);
        when(passwords.matches("wrong", "hash")).thenReturn(false);
        assertThrows(BadRequestException.class,
                () -> service.create(3L, new RemoteCommandDTO.CreateRequest("whoami", "CMD", "wrong"), 5L));
        assertThrows(BadRequestException.class,
                () -> service.create(3L, new RemoteCommandDTO.CreateRequest("whoami", "CMD"), 5L));
        verify(jobs, never()).save(any());
    }

    @Test
    void permittedRawScriptIsAuditedByFingerprintNotText() {
        when(authorization.hasOrganizationScope("REMOTE_SCRIPT_RUN")).thenReturn(true);
        when(passwords.matches("right", "hash")).thenReturn(true);
        service.create(3L, new RemoteCommandDTO.CreateRequest("echo password=hunter2", "POWERSHELL", "right"), 5L);
        verify(audit).log(eq("RemoteCommandJob"), anyLong(), eq("CREATE"),
                org.mockito.ArgumentMatchers.argThat(details -> details.contains("sha256=") && !details.contains("hunter2")));
    }

    @Test
    void actionCatalogListsEveryAction() {
        var actions = service.actions();
        assertFalse(actions.isEmpty());
        assertTrue(actions.stream().anyMatch(action -> action.key().equals("RESTART_DISPLAY_ADAPTER")));
    }
}
