package com.haodaone.document.controller;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.document.dto.EmployeeDocumentDTO;
import com.haodaone.document.service.EmployeeDocumentService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/documents")
public class EmployeeDocumentController {

    private final EmployeeDocumentService documentService;

    public EmployeeDocumentController(EmployeeDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("@authorizationService.isAllowed('EMPLOYEE_VIEW', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    public List<EmployeeDocumentDTO> byEmployee(@PathVariable Long employeeId) {
        return documentService.byEmployee(employeeId);
    }

    /** Org-wide expiring-soon list - the Dashboard widget and a future Settings-wide view both use this. */
    @GetMapping("/expiring-soon")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public List<EmployeeDocumentDTO> expiringSoon(@RequestParam(required = false) Integer days) {
        return documentService.expiringSoon(days);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public ResponseEntity<EmployeeDocumentDTO> create(@Valid @RequestBody EmployeeDocumentDTO.CreateRequest request) {
        return ResponseEntity.status(201).body(documentService.create(request));
    }

    @PostMapping(value = "/employee/{employeeId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@authorizationService.isAllowed('EMPLOYEE_MANAGE', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    public ResponseEntity<EmployeeDocumentDTO> upload(@PathVariable Long employeeId,
                                                   @RequestPart("file") MultipartFile file,
                                                   @RequestParam("documentType") String documentType,
                                                   @RequestParam(value = "documentNumber", required = false) String documentNumber,
                                                   @RequestParam(value = "issueDate", required = false) String issueDate,
                                                   @RequestParam(value = "expiryDate", required = false) String expiryDate,
                                                   @RequestParam(value = "notes", required = false) String notes) {
        LocalDate parsedIssueDate = parseDate("issueDate", issueDate, true);
        LocalDate parsedExpiryDate = parseDate("expiryDate", expiryDate, true);
        return ResponseEntity.status(201).body(documentService.upload(employeeId, file, documentType, documentNumber, parsedIssueDate, parsedExpiryDate, notes));
    }

    static LocalDate parseDate(String field, String value, boolean optional) {
        if (value == null || value.isBlank()) {
            if (optional) return null;
            throw new BadRequestException(field + " is required.");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(field + " must use ISO format yyyy-MM-dd.");
        }
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public ResponseEntity<EmployeeDocumentDTO> review(@PathVariable Long id,
                                                    @Valid @RequestBody EmployeeDocumentDTO.ReviewRequest request) {
        if (request.getApproved() == null) {
            throw new BadRequestException("Approval decision is required.");
        }
        return ResponseEntity.ok(documentService.review(id, request.getApproved(), request.getRejectionReason()));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE') or @employeeSecurity.isSelf(@employeeDocumentService.resolveEmployeeIdForDocument(#id))")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        InputStreamResource resource = documentService.download(id);
        String filename = documentService.byEmployee(documentService.resolveEmployeeIdForDocument(id)).stream()
                .filter(doc -> doc.getId().equals(id))
                .findFirst()
                .map(doc -> doc.getOriginalFileName() != null && !doc.getOriginalFileName().isBlank() ? doc.getOriginalFileName() : "document")
                .orElse("document");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename.replace("\"", "") + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
