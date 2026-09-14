package com.haodaone.company.controller;

import com.haodaone.company.dto.CompanyAdminDTO;
import com.haodaone.company.dto.SubscriptionAdminDTO;
import com.haodaone.company.service.CompanyAdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("@companySecurity.isSuperAdmin()")
public class CompanyAdminController {
    private final CompanyAdminService service;
    public CompanyAdminController(CompanyAdminService service) { this.service = service; }
    @GetMapping("/companies") public List<CompanyAdminDTO> companies() { return service.companies(); }
    @PostMapping("/companies") public ResponseEntity<CompanyAdminDTO> createCompany(@Valid @RequestBody CompanyAdminDTO.WriteRequest request) { return ResponseEntity.status(201).body(service.createCompany(request)); }
    @PutMapping("/companies/{id}") public CompanyAdminDTO updateCompany(@PathVariable Long id, @Valid @RequestBody CompanyAdminDTO.WriteRequest request) { return service.updateCompany(id, request); }
    @GetMapping("/subscriptions") public List<SubscriptionAdminDTO> subscriptions() { return service.subscriptions(); }
    @PostMapping("/subscriptions") public ResponseEntity<SubscriptionAdminDTO> createSubscriptionAtRoot(@RequestParam Long companyId, @Valid @RequestBody SubscriptionAdminDTO.WriteRequest request) { return ResponseEntity.status(201).body(service.createSubscription(companyId, request)); }
    @PostMapping("/companies/{companyId}/subscription") public ResponseEntity<SubscriptionAdminDTO> createSubscription(@PathVariable Long companyId, @Valid @RequestBody SubscriptionAdminDTO.WriteRequest request) { return ResponseEntity.status(201).body(service.createSubscription(companyId, request)); }
    @PutMapping("/subscriptions/{id}") public SubscriptionAdminDTO updateSubscription(@PathVariable Long id, @Valid @RequestBody SubscriptionAdminDTO.WriteRequest request) { return service.updateSubscription(id, request); }
}