package com.haodaone.document;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
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
                "aadhaar.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "test-pdf-content".getBytes()
        );
        String objectKey = "employee-documents/" + company.getId() + "/" + employee.getId()
                + "/AADHAAR/8f31c2-document.docx";
        when(documentStorageService.store(any(), eq(company.getId()), eq(employee.getId()), eq("AADHAAR")))
                .thenReturn(new EmployeeDocumentS3StorageService.StoredFile(objectKey, "aadhaar.docx",
                        file.getSize(), file.getContentType()));

        EmployeeDocumentDTO created = employeeDocumentService.upload(
                employee.getId(),
                file,
                "AADHAAR",
                "ABC123",
                LocalDate.now().minusYears(1),
                LocalDate.now().plusYears(2),
                "Passport copy"
        );

        assertNotNull(created.getId());
        assertEquals("PENDING_REVIEW", created.getStatus());
        assertEquals(objectKey, created.getS3ObjectKey());
        var persisted = employeeDocumentRepository.findById(created.getId()).orElseThrow();
        assertEquals("AADHAAR", persisted.getDocumentType());
        assertEquals("aadhaar.docx", persisted.getOriginalFileName());
        assertEquals(file.getSize(), persisted.getFileSizeBytes());

        EmployeeDocumentDTO reviewed = employeeDocumentService.review(created.getId(), true, null);
        assertEquals("APPROVED", reviewed.getStatus());
        assertNull(reviewed.getRejectionReason());

        assertEquals(1, employeeDocumentRepository.findAllByCompany_IdAndEmployeeIdAndDeletedFalseOrderByExpiryDateAsc(company.getId(), employee.getId()).size());
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
