package com.haodaone.document.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.document.dto.EmployeeDocumentDTO;
import com.haodaone.document.entity.EmployeeDocument;
import com.haodaone.document.repository.EmployeeDocumentRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import com.haodaone.tenant.TenantContext;

@Service
public class EmployeeDocumentService {

    /**
     * A starting set, not an exhaustive standard - chosen from what's
     * commonly tracked (ID proof, travel/work authorization,
     * certifications, contracts) since no company-specific list was
     * given. OTHER covers anything that doesn't fit; HR can request more
     * specific types be added once real usage shows what's missing.
     */
    private static final Set<String> VALID_TYPES = Set.of(
            "AADHAAR", "PAN", "EXPERIENCE_LETTER", "ID_PROOF", "PASSPORT", "WORK_VISA",
            "PROFESSIONAL_CERTIFICATION", "EMPLOYMENT_CONTRACT", "OTHER");

    private static final int DEFAULT_LOOKAHEAD_DAYS = 30;

    private final EmployeeDocumentRepository documentRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;
    private final com.haodaone.security.AuthorizationService authorizationService;
    private final EmployeeDocumentS3StorageService documentStorageService;

    public EmployeeDocumentService(EmployeeDocumentRepository documentRepository, EmployeeRepository employeeRepository,
                                    AuditLogService auditLogService,
                                    com.haodaone.security.AuthorizationService authorizationService,
                                    EmployeeDocumentS3StorageService documentStorageService) {
        this.documentRepository = documentRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogService = auditLogService;
        this.authorizationService = authorizationService;
        this.documentStorageService = documentStorageService;
    }

    public List<EmployeeDocumentDTO> byEmployee(Long employeeId) {
        Long companyId = requiredTenant();
        return documentRepository.findAllByCompany_IdAndEmployeeIdAndDeletedFalseOrderByExpiryDateAsc(companyId, employeeId).stream()
                .map(EmployeeDocumentDTO::from)
                .toList();
    }

    /** Everyone's documents expiring within the next `lookaheadDays` (default 30), soonest first. */
    public List<EmployeeDocumentDTO> expiringSoon(Integer lookaheadDays) {
        int days = lookaheadDays != null ? lookaheadDays : DEFAULT_LOOKAHEAD_DAYS;
        LocalDate today = LocalDate.now();
        var scope = authorizationService.resolveEmployeeIds("EMPLOYEE_VIEW");
        if (scope.isPresent() && scope.get().isEmpty()) return List.of();
        var rows = scope.isEmpty()
            ? documentRepository.findAllByCompany_IdAndDeletedFalseAndExpiryDateBetweenOrderByExpiryDateAsc(requiredTenant(), today, today.plusDays(days))
            : documentRepository.findAllByCompany_IdAndEmployeeIdInAndDeletedFalseAndExpiryDateBetweenOrderByExpiryDateAsc(requiredTenant(), scope.get(), today, today.plusDays(days));
        return rows.stream()
                .map(EmployeeDocumentDTO::from)
                .toList();
    }

    @Transactional
    public EmployeeDocumentDTO create(EmployeeDocumentDTO.CreateRequest request) {
        if (!VALID_TYPES.contains(request.getDocumentType())) {
            throw new BadRequestException("Unknown document type: " + request.getDocumentType() + ". Must be one of " + VALID_TYPES);
        }
        if (request.getIssueDate() != null && request.getIssueDate().isAfter(request.getExpiryDate())) {
            throw new BadRequestException("Issue date can't be after the expiry date.");
        }

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new BadRequestException("Unknown employee: " + request.getEmployeeId()));
        Long companyId = requiredTenant();
        if (employee.getCompany() == null || !companyId.equals(employee.getCompany().getId())) throw new BadRequestException("Employee is not in this company");

        EmployeeDocument doc = new EmployeeDocument();
        doc.setEmployee(employee);
        doc.setCompany(employee.getCompany());
        doc.setDocumentType(request.getDocumentType());
        doc.setDocumentNumber(request.getDocumentNumber());
        doc.setIssueDate(request.getIssueDate());
        doc.setExpiryDate(request.getExpiryDate());
        doc.setNotes(request.getNotes());

        EmployeeDocument saved = documentRepository.save(doc);
        auditLogService.log("EmployeeDocument", saved.getId(), "CREATE",
                "Added " + saved.getDocumentType() + " (expires " + saved.getExpiryDate() + ") for " + employee.getFullName());
        return EmployeeDocumentDTO.from(saved);
    }

    @Transactional
    public EmployeeDocumentDTO upload(Long employeeId, MultipartFile file, String documentType,
                                     String documentNumber, LocalDate issueDate, LocalDate expiryDate,
                                     String notes) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please attach a document file.");
        }
        if (documentType == null || documentType.isBlank()) {
            throw new BadRequestException("Document type is required.");
        }
        if (!VALID_TYPES.contains(documentType)) {
            throw new BadRequestException("Unknown document type: " + documentType + ". Must be one of " + VALID_TYPES);
        }
        if (expiryDate == null) {
            throw new BadRequestException("Expiry date is required.");
        }
        if (issueDate != null && issueDate.isAfter(expiryDate)) {
            throw new BadRequestException("Issue date can't be after the expiry date.");
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BadRequestException("Unknown employee: " + employeeId));
        Long companyId = requiredTenant();
        if (employee.getCompany() == null || !companyId.equals(employee.getCompany().getId())) {
            throw new BadRequestException("Employee is not in this company");
        }

        EmployeeDocumentS3StorageService.StoredFile stored = documentStorageService.store(
            file, companyId, employee.getId(), documentType);
        EmployeeDocument doc = new EmployeeDocument();
        doc.setEmployee(employee);
        doc.setCompany(employee.getCompany());
        doc.setDocumentType(documentType);
        doc.setDocumentNumber(documentNumber);
        doc.setIssueDate(issueDate);
        doc.setExpiryDate(expiryDate);
        doc.setNotes(notes);
        doc.setStatus(EmployeeDocument.STATUS_PENDING_REVIEW);
        doc.setS3ObjectKey(stored.key());
        doc.setOriginalFileName(stored.originalName());
        doc.setContentType(stored.contentType());
        doc.setFileSizeBytes(stored.sizeBytes());
        doc.setUploadedAt(LocalDateTime.now());

        EmployeeDocument saved = documentRepository.save(doc);
        auditLogService.log("EmployeeDocument", saved.getId(), "UPLOAD",
                "Uploaded " + saved.getDocumentType() + " for " + employee.getFullName());
        return EmployeeDocumentDTO.from(saved);
    }

    @Transactional
    public EmployeeDocumentDTO review(Long id, boolean approved, String rejectionReason) {
        EmployeeDocument doc = documentRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));

        if (approved) {
            doc.setStatus(EmployeeDocument.STATUS_APPROVED);
            doc.setRejectionReason(null);
            doc.setReviewedAt(LocalDateTime.now());
            doc.setReviewedByEmployee(resolveCurrentEmployee());
            EmployeeDocument saved = documentRepository.save(doc);
            auditLogService.log("EmployeeDocument", saved.getId(), "APPROVE",
                    "Approved " + saved.getDocumentType() + " for " + saved.getEmployee().getFullName());
            return EmployeeDocumentDTO.from(saved);
        }

        String normalizedReason = rejectionReason == null ? "" : rejectionReason.trim();
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("A rejection reason is required when rejecting a document.");
        }
        doc.setStatus(EmployeeDocument.STATUS_REJECTED);
        doc.setRejectionReason(normalizedReason);
        doc.setReviewedAt(LocalDateTime.now());
        doc.setReviewedByEmployee(resolveCurrentEmployee());
        EmployeeDocument saved = documentRepository.save(doc);
        auditLogService.log("EmployeeDocument", saved.getId(), "REJECT",
                "Rejected " + saved.getDocumentType() + " for " + saved.getEmployee().getFullName() + ": " + normalizedReason);
        return EmployeeDocumentDTO.from(saved);
    }

    public InputStreamResource download(Long id) {
        EmployeeDocument doc = documentRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
        if (doc.getS3ObjectKey() == null || doc.getS3ObjectKey().isBlank()) {
            throw new BadRequestException("Document file is not available.");
        }
        return documentStorageService.retrieve(doc.getS3ObjectKey());
    }

    public Long resolveEmployeeIdForDocument(Long id) {
        return documentRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .map(doc -> doc.getEmployee().getId())
                .orElse(null);
    }

    @Transactional
    public void delete(Long id) {
        EmployeeDocument doc = documentRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
        if (doc.getS3ObjectKey() != null && !doc.getS3ObjectKey().isBlank()) {
            documentStorageService.delete(doc.getS3ObjectKey());
        }
        doc.setDeleted(true);
        documentRepository.save(doc);
        auditLogService.log("EmployeeDocument", doc.getId(), "DELETE",
                "Removed " + doc.getDocumentType() + " for " + doc.getEmployee().getFullName());
    }

    private Employee resolveCurrentEmployee() {
        return authorizationService.resolveCurrentEmployee();
    }

    private Long requiredTenant() { Long tenant = TenantContext.getCurrentTenant(); if (tenant == null) throw new BadRequestException("Company context is required"); return tenant; }
}
