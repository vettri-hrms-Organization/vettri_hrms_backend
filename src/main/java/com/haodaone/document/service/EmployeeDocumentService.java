package com.haodaone.document.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.document.dto.EmployeeDocumentDTO;
import com.haodaone.document.entity.EmployeeDocument;
import com.haodaone.document.repository.EmployeeDocumentRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    public EmployeeDocumentService(EmployeeDocumentRepository documentRepository, EmployeeRepository employeeRepository,
                                    AuditLogService auditLogService) {
        this.documentRepository = documentRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogService = auditLogService;
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
        return documentRepository.findAllByCompany_IdAndDeletedFalseAndExpiryDateBetweenOrderByExpiryDateAsc(requiredTenant(), today, today.plusDays(days)).stream()
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
    public void delete(Long id) {
        EmployeeDocument doc = documentRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
        doc.setDeleted(true);
        documentRepository.save(doc);
        auditLogService.log("EmployeeDocument", doc.getId(), "DELETE",
                "Removed " + doc.getDocumentType() + " for " + doc.getEmployee().getFullName());
    }

    private Long requiredTenant() { Long tenant = TenantContext.getCurrentTenant(); if (tenant == null) throw new BadRequestException("Company context is required"); return tenant; }
}
