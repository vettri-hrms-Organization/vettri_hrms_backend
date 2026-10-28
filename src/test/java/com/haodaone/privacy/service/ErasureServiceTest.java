package com.haodaone.privacy.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.document.entity.EmployeeDocument;
import com.haodaone.document.repository.EmployeeDocumentRepository;
import com.haodaone.document.service.EmployeeDocumentS3StorageService;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.privacy.dto.PrivacyDTO;
import com.haodaone.privacy.entity.CompanyPrivacySetting;
import com.haodaone.privacy.entity.DataPrincipalRequest;
import com.haodaone.privacy.repository.DataPrincipalRequestRepository;
import com.haodaone.privacy.repository.MonitoringConsentRepository;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ErasureServiceTest {

    private final DataPrincipalRequestRepository requestRepository = mock(DataPrincipalRequestRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PrivacySettingsService settingsService = mock(PrivacySettingsService.class);
    private final PersonalDataExportService exportService = mock(PersonalDataExportService.class);
    private final EmployeeDocumentRepository documentRepository = mock(EmployeeDocumentRepository.class);
    private final MonitoringConsentRepository consentRepository = mock(MonitoringConsentRepository.class);
    private final EmployeeDocumentS3StorageService storage = mock(EmployeeDocumentS3StorageService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final NotificationService notificationService = mock(NotificationService.class);

    private EmployeeAnonymisationService anonymisationService;
    private DataPrincipalRequestService service;
    private Company company;
    private Employee employee;
    private User user;
    private CompanyPrivacySetting settings;

    @BeforeEach
    void setUp() {
        anonymisationService = new EmployeeAnonymisationService(employeeRepository, userRepository, documentRepository,
                consentRepository, auditLogService);
        anonymisationService.setDocumentStorage(storage);
        service = new DataPrincipalRequestService(requestRepository, employeeRepository, userRepository, settingsService,
                exportService, anonymisationService, auditLogService, notificationService);

        company = new Company();
        company.setId(3L);
        company.setName("Acme");
        user = new User();
        user.setId(70L);
        user.setUsername("priya@acme.test");
        user.setEmail("priya@acme.test");
        user.setFullName("Priya Raman");
        user.setActive(true);
        employee = new Employee();
        employee.setId(42L);
        employee.setCompany(company);
        employee.setUser(user);
        employee.setEmployeeCode("EMP042");
        employee.setFirstName("Priya");
        employee.setLastName("Raman");
        employee.setEmail("priya@acme.test");
        employee.setPhone("9876543210");
        employee.setPanNumber("ABCDE1234F");
        employee.setBankAccountNumber("123456789012");
        employee.setBankIfsc("HDFC0000123");
        employee.setBankName("HDFC Bank");
        employee.setAddress("12 MG Road, Chennai");
        employee.setUanNumber("100200300400");
        settings = new CompanyPrivacySetting();
        settings.setOtherDataRetentionMonths(24);

        TenantContext.setCurrentTenant(3L);
        when(employeeRepository.findByUser_IdAndDeletedFalse(70L)).thenReturn(Optional.of(employee));
        when(settingsService.settingsFor(3L)).thenReturn(settings);
        when(requestRepository.save(any(DataPrincipalRequest.class))).thenAnswer(inv -> {
            DataPrincipalRequest r = inv.getArgument(0);
            if (r.getId() == null) r.setId(500L);
            return r;
        });
        when(userRepository.findAllByCompanyIdAndDeletedFalse(3L)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void erasureRequestFromActiveEmployeeIsRejectedWithExplanation() {
        employee.setStatus(EmploymentStatus.ACTIVE);

        PrivacyDTO.DataRequestView view = service.create(70L, new PrivacyDTO.DataRequestCreate("ERASURE", "Please delete me"));

        assertEquals(DataPrincipalRequest.STATUS_REJECTED, view.status());
        assertTrue(view.resolutionNote().contains("not available while you are employed"));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void exitedEmployeeInsideRetentionPeriodIsRejectedWithEligibleDate() {
        employee.setStatus(EmploymentStatus.RESIGNED);
        employee.setLastWorkingDate(LocalDate.now().minusMonths(3));

        PrivacyDTO.DataRequestView view = service.create(70L, new PrivacyDTO.DataRequestCreate("ERASURE", null));

        assertEquals(DataPrincipalRequest.STATUS_REJECTED, view.status());
        assertTrue(view.resolutionNote().contains(employee.getLastWorkingDate().plusMonths(24).toString()));
    }

    @Test
    void hrErasureAnonymisesPersonalFieldsDeletesDocumentsAndKeepsPayroll() {
        employee.setStatus(EmploymentStatus.TERMINATED);
        employee.setLastWorkingDate(LocalDate.now().minusYears(3));

        DataPrincipalRequest request = new DataPrincipalRequest();
        request.setId(501L);
        request.setCompany(company);
        request.setEmployee(employee);
        request.setRequestType(DataPrincipalRequest.TYPE_ERASURE);
        request.setStatus(DataPrincipalRequest.STATUS_SUBMITTED);
        request.setDueDate(LocalDate.now().plusDays(30));
        when(requestRepository.findByIdAndCompany_IdAndDeletedFalse(501L, 3L)).thenReturn(Optional.of(request));

        EmployeeDocument doc = new EmployeeDocument();
        doc.setId(9L);
        doc.setEmployee(employee);
        doc.setS3ObjectKey("companies/3/employees/42/pan.pdf");
        doc.setDocumentNumber("ABCDE1234F");
        doc.setOriginalFileName("pan.pdf");
        when(documentRepository.findAllByCompany_IdAndEmployeeIdAndDeletedFalseOrderByExpiryDateAsc(3L, 42L))
                .thenReturn(List.of(doc));
        when(consentRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByGrantedAtDesc(3L, 42L))
                .thenReturn(List.of());

        PrivacyDTO.ErasureResult result = service.erase(501L, 1L);

        assertEquals("Erased", employee.getFirstName());
        assertEquals("Employee #42", employee.getLastName());
        assertEquals("erased-42@erased.invalid", employee.getEmail());
        assertNull(employee.getPhone());
        assertNull(employee.getPanNumber());
        assertNull(employee.getBankAccountNumber());
        assertNull(employee.getBankIfsc());
        assertNull(employee.getBankName());
        assertNull(employee.getAddress());
        assertEquals("EMP042", employee.getEmployeeCode(), "employee code is the retained pseudonymous anchor");
        assertEquals("100200300400", employee.getUanNumber(), "UAN kept while the 7-year payroll retention runs");
        assertFalse(user.isActive());
        assertEquals("erased-user-70", user.getUsername());

        verify(storage).delete("companies/3/employees/42/pan.pdf");
        assertTrue(doc.isDeleted());
        assertNull(doc.getDocumentNumber());
        assertEquals(1, result.documentsDeleted());
        assertTrue(result.anonymisedFields().containsAll(List.of("name", "email", "phone", "pan", "bankAccount", "address")));
        assertTrue(result.retained().stream().anyMatch(r -> r.contains("Payroll items")));
        assertEquals(DataPrincipalRequest.STATUS_COMPLETED, request.getStatus());
    }

    @Test
    void hrCannotEraseActiveEmployeeEvenFromQueue() {
        employee.setStatus(EmploymentStatus.ACTIVE);
        DataPrincipalRequest request = new DataPrincipalRequest();
        request.setId(502L);
        request.setCompany(company);
        request.setEmployee(employee);
        request.setRequestType(DataPrincipalRequest.TYPE_ERASURE);
        request.setStatus(DataPrincipalRequest.STATUS_SUBMITTED);
        when(requestRepository.findByIdAndCompany_IdAndDeletedFalse(502L, 3L)).thenReturn(Optional.of(request));

        assertThrows(BadRequestException.class, () -> service.erase(502L, 1L));
        assertEquals("Priya", employee.getFirstName());
    }
}
