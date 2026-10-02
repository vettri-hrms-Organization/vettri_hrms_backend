package com.haodaone.software.controller;

import com.haodaone.software.dto.SoftwareDeploymentDTO;
import com.haodaone.software.dto.SoftwarePackageDTO;
import com.haodaone.software.dto.SoftwareVersionDTO;
import com.haodaone.software.dto.SoftwareDeploymentTargetDTO;
import com.haodaone.software.service.SoftwareManagementService;
import com.haodaone.security.CustomUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/software")
public class SoftwareController {

    private final SoftwareManagementService softwareManagementService;

    public SoftwareController(SoftwareManagementService softwareManagementService) {
        this.softwareManagementService = softwareManagementService;
    }

    @GetMapping("/packages")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.isAllowed('SOFTWARE_VIEW', null, null)")
    public List<SoftwarePackageDTO> listPackages() {
        return softwareManagementService.listPackages();
    }

    @GetMapping("/packages/{id}")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.isAllowed('SOFTWARE_VIEW', null, null)")
    public SoftwarePackageDTO getPackage(@PathVariable Long id) {
        return softwareManagementService.getPackage(id);
    }

    @PostMapping("/packages")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.isAllowed('SOFTWARE_MANAGE', null, null)")
    public ResponseEntity<SoftwarePackageDTO> createPackage(@Valid @RequestBody SoftwarePackageDTO.CreateRequest request) {
        return ResponseEntity.status(201).body(softwareManagementService.createPackage(request));
    }

    @GetMapping("/packages/{packageId}/versions")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.isAllowed('SOFTWARE_VIEW', null, null)")
    public List<SoftwareVersionDTO> listVersions(@PathVariable Long packageId) {
        return softwareManagementService.listVersions(packageId);
    }

    @PostMapping("/packages/{packageId}/versions")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.isAllowed('SOFTWARE_MANAGE', null, null)")
    public ResponseEntity<SoftwareVersionDTO> createVersion(@PathVariable Long packageId,
                                                           @Valid @RequestPart("metadata") SoftwareVersionDTO.CreateRequest request,
                                                           @RequestPart("installer") MultipartFile installer) {
        return ResponseEntity.status(201).body(softwareManagementService.createVersion(packageId, request, installer));
    }

    @GetMapping("/deployments")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and (@authorizationService.isAllowed('SOFTWARE_VIEW', null, null) or @authorizationService.isAllowed('SOFTWARE_DEPLOY', null, null))")
    public List<SoftwareDeploymentDTO> listDeployments() {
        return softwareManagementService.listDeployments();
    }

    @GetMapping("/deployments/{id}/targets")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and (@authorizationService.isAllowed('SOFTWARE_VIEW', null, null) or @authorizationService.isAllowed('SOFTWARE_DEPLOY', null, null))")
    public List<SoftwareDeploymentTargetDTO> deploymentTargets(@PathVariable Long id) {
        return softwareManagementService.listDeploymentTargets(id);
    }

    @PostMapping("/deployments")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and (@authorizationService.isAllowed('SOFTWARE_DEPLOY', null, null) or @authorizationService.isAllowed('SOFTWARE_MANAGE', null, null))")
    public ResponseEntity<SoftwareDeploymentDTO> createDeployment(@Valid @RequestBody SoftwareDeploymentDTO.CreateRequest request,
                                                                   @org.springframework.security.core.annotation.AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.status(201).body(softwareManagementService.createDeployment(request, principal.getId()));
    }
}
