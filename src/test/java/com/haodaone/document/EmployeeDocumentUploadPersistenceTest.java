package com.haodaone.document;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.document.entity.EmployeeDocument;
import com.haodaone.document.repository.EmployeeDocumentRepository;
import com.haodaone.document.service.EmployeeDocumentS3StorageService;
import com.haodaone.document.service.EmployeeDocumentService;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class EmployeeDocumentUploadPersistenceTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeDocumentService employeeDocumentService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private EmployeeDocumentRepository documentRepository;

    @MockitoBean
    private EmployeeDocumentS3StorageService documentStorageService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void uploadPersistsPendingDocumentAndReturnsLoadedEmployeeDetails() {
        Company company = createCompany();
        Employee employee = createEmployee(company);
        TenantContext.setCurrentTenant(company.getId());
        when(documentStorageService.store(any(MultipartFile.class), eq(company.getId()),
                eq(employee.getId()), eq("AADHAAR")))
                .thenReturn(storedFile(company, employee, "AADHAAR"));
        when(documentRepository.saveAndFlush(any(EmployeeDocument.class)))
                .thenAnswer(invocation -> {
                    EmployeeDocument saved = invocation.getArgument(0);
                    saved.setId(1L);
                    return saved;
                });

        var response = employeeDocumentService.upload(
                employee.getId(), file(), "AADHAAR", null, null, null, null);

        assertEquals(employee.getId(), response.getEmployeeId());
        assertEquals(employee.getFullName(), response.getEmployeeName());
        assertEquals(EmployeeDocument.STATUS_PENDING_REVIEW, response.getStatus());
        verify(documentRepository).saveAndFlush(any(EmployeeDocument.class));
    }

    @Test
    void uploadRejectsMissingEmployeeBeforeWritingToS3() {
        Company company = createCompany();
        TenantContext.setCurrentTenant(company.getId());

        assertThrows(BadRequestException.class, () -> employeeDocumentService.upload(
                Long.MAX_VALUE, file(), "AADHAAR", null, null, null, null));

        verify(documentStorageService, never()).store(any(MultipartFile.class), any(), any(), any());
    }

    @Test
    void uploadRejectsEmployeeFromAnotherCompanyBeforeWritingToS3() {
        Company tenant = createCompany();
        Company owner = createCompany();
        Employee employee = createEmployee(owner);
        TenantContext.setCurrentTenant(tenant.getId());

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> employeeDocumentService.upload(
                        employee.getId(), file(), "AADHAAR", null, null, null, null));

        assertEquals("Employee is not in this company", exception.getMessage());
        verify(documentStorageService, never()).store(any(MultipartFile.class), any(), any(), any());
    }

    @Test
    void uploadResponseDoesNotInitializeDetachedDocumentEmployeeProxy() {
        Company company = createCompany();
        Employee employee = createEmployee(company);
        TenantContext.setCurrentTenant(company.getId());

        Employee detachedProxy = new TransactionTemplate(transactionManager).execute(status -> {
            Employee proxy = entityManager.getReference(Employee.class, employee.getId());
            entityManager.detach(proxy);
            return proxy;
        });
        assertFalse(Hibernate.isInitialized(detachedProxy));

        when(documentStorageService.store(any(MultipartFile.class), eq(company.getId()),
                eq(employee.getId()), eq("AADHAAR")))
                .thenReturn(storedFile(company, employee, "AADHAAR"));
        when(documentRepository.saveAndFlush(any(EmployeeDocument.class)))
                .thenAnswer(invocation -> {
                    EmployeeDocument saved = invocation.getArgument(0);
                    saved.setId(1L);
                    saved.setEmployee(detachedProxy);
                    return saved;
                });

        var response = employeeDocumentService.upload(
                employee.getId(), file(), "AADHAAR", null, null, null, null);

        assertEquals(employee.getFullName(), response.getEmployeeName());
        assertFalse(Hibernate.isInitialized(detachedProxy));
    }

    @Test
    void uploadDeletesNewS3ObjectWhenDocumentPersistenceFails() {
        Company company = createCompany();
        Employee employee = createEmployee(company);
        TenantContext.setCurrentTenant(company.getId());
        EmployeeDocumentS3StorageService.StoredFile stored = storedFile(company, employee, "AADHAAR");
        when(documentStorageService.store(any(MultipartFile.class), eq(company.getId()),
                eq(employee.getId()), eq("AADHAAR"))).thenReturn(stored);
        doThrow(new DataIntegrityViolationException("simulated metadata persistence failure"))
                .when(documentRepository).saveAndFlush(any(EmployeeDocument.class));

        assertThrows(DataIntegrityViolationException.class, () -> employeeDocumentService.upload(
                employee.getId(), file(), "AADHAAR", null, null, null, null));

        verify(documentStorageService).delete(stored.key());
    }

    private Company createCompany() {
        Company company = new Company();
        company.setName("Document persistence " + UUID.randomUUID());
        return companyRepository.save(company);
    }

    private Employee createEmployee(Company company) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Employee employee = new Employee();
        employee.setEmployeeCode("D" + suffix);
        employee.setFirstName("Employee");
        employee.setLastName(String.valueOf(employeeRepository.count() + 1));
        employee.setEmail(suffix + "@document-persistence.example");
        employee.setDateOfJoining(LocalDate.now().minusMonths(1));
        employee.setCompany(company);
        return employeeRepository.saveAndFlush(employee);
    }

    private EmployeeDocumentS3StorageService.StoredFile storedFile(
            Company company, Employee employee, String documentType) {
        String key = "employee-documents/" + company.getId() + "/" + employee.getId()
                + "/" + documentType + "/test-" + UUID.randomUUID() + ".pdf";
        return new EmployeeDocumentS3StorageService.StoredFile(key, "identity.pdf", 3, "application/pdf");
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "identity.pdf", "application/pdf", new byte[]{1, 2, 3});
    }
}
