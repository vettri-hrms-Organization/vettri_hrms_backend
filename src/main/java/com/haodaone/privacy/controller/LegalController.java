package com.haodaone.privacy.controller;

import com.haodaone.privacy.dto.PrivacyDTO;
import com.haodaone.privacy.service.LegalAcceptanceService;
import com.haodaone.privacy.service.LegalDocumentService;
import com.haodaone.security.CustomUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Legal documents (Terms, Privacy, DPA, Acceptable Use) and the user's acceptance of them. */
@RestController
@RequestMapping("/api/legal")
@PreAuthorize("isAuthenticated()")
public class LegalController {

    private final LegalAcceptanceService acceptanceService;
    private final LegalDocumentService documentService;

    public LegalController(LegalAcceptanceService acceptanceService, LegalDocumentService documentService) {
        this.acceptanceService = acceptanceService;
        this.documentService = documentService;
    }

    /** Current documents + what this user still has to accept. */
    @GetMapping("/status")
    public PrivacyDTO.LegalStatus status(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return acceptanceService.status(principal.getId());
    }

    @PostMapping("/accept")
    public PrivacyDTO.LegalStatus accept(@AuthenticationPrincipal CustomUserPrincipal principal,
                                         @RequestBody PrivacyDTO.AcceptRequest request,
                                         HttpServletRequest httpRequest) {
        return acceptanceService.accept(principal.getId(), request.documentTypes(), httpRequest);
    }

    @GetMapping("/acceptances/me")
    public List<PrivacyDTO.AcceptanceView> myAcceptances(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return acceptanceService.myAcceptances(principal.getId());
    }

    /** Version history of published documents (platform admins). */
    @GetMapping("/documents/history")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<PrivacyDTO.LegalDocumentHistory> history() {
        return documentService.history();
    }
}
