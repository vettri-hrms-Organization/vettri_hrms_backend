package com.haodaone.salary.fbp;

import com.haodaone.security.CustomUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Flexible benefit plan: /me for employees; the rest for payroll admins (organization-wide SALARY_MANAGE, checked in the service). */
@RestController
@RequestMapping("/api/fbp")
@PreAuthorize("isAuthenticated()")
public class FbpController {

    private final FbpService service;

    public FbpController(FbpService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public FbpService.MyView mine(@RequestParam(required = false) Integer fy) {
        return service.mine(fy);
    }

    @PostMapping("/me/declaration")
    public FbpService.MyView declare(@RequestBody FbpService.DeclareBody body) {
        return service.declare(body);
    }

    @PostMapping("/me/claims")
    public FbpService.MyView claim(@RequestBody FbpService.ClaimBody body) {
        return service.claim(body);
    }

    @GetMapping("/components")
    public List<FbpService.ComponentDTO> components() {
        return service.listComponents();
    }

    @PostMapping("/components/defaults")
    public List<FbpService.ComponentDTO> seed() {
        return service.seedDefaults();
    }

    @PostMapping("/components")
    public FbpService.ComponentDTO add(@RequestBody FbpService.ComponentDTO body) {
        return service.saveComponent(null, body);
    }

    @PutMapping("/components/{id}")
    public FbpService.ComponentDTO update(@PathVariable Long id, @RequestBody FbpService.ComponentDTO body) {
        return service.saveComponent(id, body);
    }

    @GetMapping("/declarations")
    public List<FbpService.DeclarationDTO> declarations(@RequestParam(required = false) String status) {
        return service.declarations(status);
    }

    public record NoteBody(String note) { }

    @PostMapping("/declarations/{id}/approve")
    public FbpService.DeclarationDTO approveDeclaration(@PathVariable Long id, @RequestBody(required = false) NoteBody body, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.reviewDeclaration(id, true, body == null ? null : body.note(), name(p));
    }

    @PostMapping("/declarations/{id}/reject")
    public FbpService.DeclarationDTO rejectDeclaration(@PathVariable Long id, @RequestBody(required = false) NoteBody body, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.reviewDeclaration(id, false, body == null ? null : body.note(), name(p));
    }

    @GetMapping("/claims")
    public List<FbpService.ClaimDTO> claims(@RequestParam(required = false) String status) {
        return service.claims(status);
    }

    @PostMapping("/claims/{id}/approve")
    public FbpService.ClaimDTO approveClaim(@PathVariable Long id, @RequestBody(required = false) NoteBody body, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.reviewClaim(id, true, body == null ? null : body.note(), name(p));
    }

    @PostMapping("/claims/{id}/reject")
    public FbpService.ClaimDTO rejectClaim(@PathVariable Long id, @RequestBody(required = false) NoteBody body, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.reviewClaim(id, false, body == null ? null : body.note(), name(p));
    }

    private static String name(CustomUserPrincipal p) {
        if (p == null || p.getUser() == null) return "Payroll admin";
        String full = p.getUser().getFullName();
        return full == null || full.isBlank() ? p.getUsername() : full;
    }
}
