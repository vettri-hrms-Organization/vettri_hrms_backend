package com.haodaone.privacy.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.privacy.dto.PrivacyDTO;
import com.haodaone.privacy.entity.LegalAcceptance;
import com.haodaone.privacy.repository.LegalAcceptanceRepository;
import com.haodaone.privacy.repository.LegalDocumentRepository;
import com.haodaone.user.dto.UserDTO;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegalAcceptanceServiceTest {

    private final LegalAcceptanceRepository acceptanceRepository = mock(LegalAcceptanceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private LegalDocumentService documentService;
    private LegalAcceptanceService service;
    /** (docType + "@" + version) the fake repository considers accepted. */
    private final Set<String> accepted = new HashSet<>();

    @BeforeEach
    void setUp() {
        documentService = new LegalDocumentService(mock(LegalDocumentRepository.class));
        service = new LegalAcceptanceService(acceptanceRepository, documentService, userRepository, auditLogService);
        when(acceptanceRepository.existsByUser_IdAndDocTypeAndDocVersionAndDeletedFalse(anyLong(), anyString(), anyString()))
                .thenAnswer(inv -> accepted.contains(inv.getArgument(1) + "@" + inv.getArgument(2)));
        when(acceptanceRepository.save(any(LegalAcceptance.class))).thenAnswer(inv -> {
            LegalAcceptance a = inv.getArgument(0);
            a.setId(1L);
            accepted.add(a.getDocType() + "@" + a.getDocVersion());
            return a;
        });
    }

    private static User user(String roleName) {
        Company company = new Company();
        company.setId(5L);
        company.setName("Acme");
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(9L);
        user.setUsername("admin@acme.test");
        user.setCompany(company);
        user.setRoles(new HashSet<>(Set.of(role)));
        return user;
    }

    @Test
    void companyAdminMustAcceptTermsPrivacyAupAndDpa() {
        User admin = user("COMPANY_ADMIN");
        List<String> pending = service.pendingFor(admin).stream().map(PrivacyDTO.LegalDocumentInfo::type).toList();
        assertEquals(List.of("TERMS", "PRIVACY", "ACCEPTABLE_USE", "DPA"), pending);

        service.recordSignupAcceptance(admin, admin.getCompany(), null);
        assertTrue(service.pendingFor(admin).isEmpty());
    }

    @Test
    void versionBumpForcesReAcceptanceOnNextLogin() {
        User admin = user("COMPANY_ADMIN");
        service.recordSignupAcceptance(admin, admin.getCompany(), null);
        UserDTO before = new UserDTO();
        service.applyTo(before, admin);
        assertFalse(before.isLegalAcceptanceRequired());

        // Publish a new Terms version.
        ReflectionTestUtils.setField(documentService, "termsVersion", "2027-01-01");

        UserDTO after = new UserDTO();
        service.applyTo(after, admin);
        assertTrue(after.isLegalAcceptanceRequired());
        assertEquals(List.of("TERMS"), after.getPendingLegalDocuments());

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        PrivacyDTO.LegalStatus status = service.accept(9L, List.of("TERMS"), null);
        assertFalse(status.acceptanceRequired());
        assertTrue(accepted.contains("TERMS@2027-01-01"));
    }

    @Test
    void employeeOnlyNeedsPrivacyAndAcceptableUseAndCannotAcceptDpa() {
        User employee = user("EMPLOYEE");
        List<String> pending = service.pendingFor(employee).stream().map(PrivacyDTO.LegalDocumentInfo::type).toList();
        assertEquals(List.of("PRIVACY", "ACCEPTABLE_USE"), pending);

        when(userRepository.findById(9L)).thenReturn(Optional.of(employee));
        assertThrows(BadRequestException.class, () -> service.accept(9L, List.of("DPA"), null));
    }

    @Test
    void superAdminIsNeverGated() {
        UserDTO dto = new UserDTO();
        service.applyTo(dto, user("SUPER_ADMIN"));
        assertFalse(dto.isLegalAcceptanceRequired());
    }
}
