package com.haodaone.document.controller;

import com.haodaone.document.dto.EmployeeDocumentDTO;
import com.haodaone.document.service.EmployeeDocumentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class EmployeeDocumentController {

    private final EmployeeDocumentService documentService;

    public EmployeeDocumentController(EmployeeDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW') or @employeeSecurity.isSelf(#employeeId)")
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
