package com.haodaone.requirements.controller;

import com.haodaone.requirements.dto.RequirementDTO;
import com.haodaone.requirements.entity.RequirementStatus;
import com.haodaone.requirements.service.RequirementService;
import com.haodaone.security.CustomUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/requirements")
public class RequirementController {
    private final RequirementService service;
    public RequirementController(RequirementService service) { this.service = service; }
    @GetMapping @PreAuthorize("isAuthenticated()") public List<RequirementDTO> list(@RequestParam(required = false) RequirementStatus status) { return service.list(status); }
    @PostMapping @PreAuthorize("hasAuthority('REQUIREMENT_MANAGE')") public ResponseEntity<RequirementDTO> create(@Valid @RequestBody RequirementDTO.WriteRequest request, @AuthenticationPrincipal CustomUserPrincipal principal) { return ResponseEntity.status(201).body(service.create(request, principal)); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('REQUIREMENT_MANAGE')") public RequirementDTO update(@PathVariable Long id, @Valid @RequestBody RequirementDTO.WriteRequest request) { return service.update(id, request); }
    @PatchMapping("/{id}/status") @PreAuthorize("hasAuthority('REQUIREMENT_MANAGE')") public RequirementDTO status(@PathVariable Long id, @RequestBody RequirementDTO.StatusRequest request, @AuthenticationPrincipal CustomUserPrincipal principal) { return service.changeStatus(id, request, principal.getUser()); }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('REQUIREMENT_MANAGE')") public ResponseEntity<Void> delete(@PathVariable Long id) { service.delete(id); return ResponseEntity.noContent().build(); }
}