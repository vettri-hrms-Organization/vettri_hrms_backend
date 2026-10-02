package com.haodaone.document;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.document.dto.EmployeeDocumentDTO;
import com.haodaone.document.repository.EmployeeDocumentRepository;
import com.haodaone.document.service.EmployeeDocumentS3StorageService;
import com.haodaone.document.service.EmployeeDocumentService;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class EmployeeDocumentUploadReviewWorkflowTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeDocumentService employeeDocumentService;

    @Autowired
    private EmployeeDocumentRepository employeeDocumentRepository;

        @MockitoBean
        private EmployeeDocumentS3StorageService documentStorageService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void upload_and_review_document_lifecycle() {
        Company company = new Company();
        company.setName("DocCo");
        company = companyRepository.save(company);
        TenantContext.setCurrentTenant(company.getId());

        Employee employee = new Employee();
        employee.setCompany(company);
        employee.setEmployeeCode("EMP-100");
        employee.setFirstName("Test");
        employee.setLastName("User");
        employee.setEmail("test.user+doc@example.com");
        employee.setDateOfJoining(LocalDate.now().minusMonths(3));
        employee = employeeRepository.save(employee);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "aadhaar.pdf",
                "application/pdf",
                "test-pdf-content".getBytes()
        );
        String objectKey = "employee-documents/" + company.getId() + "/" + employee.getId()
                + "/AADHAAR/8f31c2-document.pdf";
        when(documentStorageService.store(any(), eq(company.getId()), eq(employee.getId()), eq("AADHAAR")))
                .thenReturn(new EmployeeDocumentS3StorageService.StoredFile(objectKey, "aadhaar.pdf",
                        file.getSize(), file.getContentType()));

        EmployeeDocumentDTO created = employeeDocumentService.upload(
                employee.getId(),
                file,
                "AADHAAR",
                null,
                null,
                null,
                null
        );

        assertNotNull(created.getId());
        assertEquals("PENDING_REVIEW", created.getStatus());
        assertEquals(objectKey, created.getS3ObjectKey());
        var persisted = employeeDocumentRepository.findById(created.getId()).orElseThrow();
        assertEquals("AADHAAR", persisted.getDocumentType());
        assertEquals("aadhaar.pdf", persisted.getOriginalFileName());
        assertEquals(file.getSize(), persisted.getFileSizeBytes());
        assertNull(persisted.getDocumentNumber());
        assertNull(persisted.getIssueDate());
        assertNull(persisted.getExpiryDate());

        EmployeeDocumentDTO reviewed = employeeDocumentService.review(created.getId(), true, null);
        assertEquals("APPROVED", reviewed.getStatus());
        assertNull(reviewed.getRejectionReason());

        assertEquals(1, employeeDocumentRepository.findAllByCompany_IdAndEmployeeIdAndDeletedFalseOrderByExpiryDateAsc(company.getId(), employee.getId()).size());
    }

    @Test
    void pan_without_expiry_can_be_uploaded() {
        Company company = new Company();
        company.setName("PanCo");
        company = companyRepository.save(company);
        TenantContext.setCurrentTenant(company.getId());

        Employee employee = new Employee();
        employee.setCompany(company);
        employee.setEmployeeCode("EMP-101");
        employee.setFirstName("Pan");
        employee.setLastName("User");
        employee.setEmail("pan.user+doc@example.com");
        employee.setDateOfJoining(LocalDate.now().minusMonths(3));
        employee = employeeRepository.save(employee);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "pan.pdf",
                "application/pdf",
                "test-pdf-content".getBytes()
        );
        String objectKey = "employee-documents/" + company.getId() + "/" + employee.getId()
                + "/PAN/8f31c2-document.pdf";
        when(documentStorageService.store(any(), eq(company.getId()), eq(employee.getId()), eq("PAN")))
                .thenReturn(new EmployeeDocumentS3StorageService.StoredFile(objectKey, "pan.pdf",
                        file.getSize(), file.getContentType()));

        EmployeeDocumentDTO created = employeeDocumentService.upload(
                employee.getId(), file, "PAN", null, null, null, null);

        assertNull(created.getDocumentNumber());
        assertNull(created.getIssueDate());
        assertNull(created.getExpiryDate());
        assertNull(employeeDocumentRepository.findById(created.getId()).orElseThrow().getExpiryDate());
    }

    @Test
    void passport_upload_still_requires_expiry_date() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "passport.pdf", "application/pdf", "test-pdf-content".getBytes());

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> employeeDocumentService.upload(1L, file, "PASSPORT", null, null, null, null));

        assertEquals("Expiry date is required.", exception.getMessage());
    }

    @Test
    void rejected_document_requires_reason() {
        Company company = new Company();
        company.setName("RejectCo");
        company = companyRepository.save(company);
        TenantContext.setCurrentTenant(company.getId());

        Employee employee = new Employee();
        employee.setCompany(company);
        employee.setEmployeeCode("EMP-200");
        employee.setFirstName("Another");
        employee.setLastName("User");
        employee.setEmail("another.user+doc@example.com");
        employee.setDateOfJoining(LocalDate.now().minusMonths(2));
        employee = employeeRepository.save(employee);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "passport.pdf",
                "application/pdf",
                "passport-content".getBytes()
        );
        when(documentStorageService.store(any(), eq(company.getId()), eq(employee.getId()), eq("PASSPORT")))
                .thenReturn(new EmployeeDocumentS3StorageService.StoredFile(
                        "employee-documents/" + company.getId() + "/" + employee.getId() + "/PASSPORT/test.pdf",
                        "passport.pdf", file.getSize(), file.getContentType()));

        EmployeeDocumentDTO created = employeeDocumentService.upload(
                employee.getId(),
                file,
                "PASSPORT",
                "XYZ999",
                LocalDate.now().minusYears(1),
                LocalDate.now().plusYears(1),
                "Needs check"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> employeeDocumentService.review(created.getId(), false, null));
        assertTrue(ex.getMessage().contains("rejection reason"));
    }
}
