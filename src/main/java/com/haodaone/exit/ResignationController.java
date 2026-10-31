package com.haodaone.exit;

import com.haodaone.security.CustomUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Resignations. /me acts on the caller's own record; reviewers (the reporting manager, HR with
 * EMPLOYEE_MANAGE) act on the rest - the service checks who may do what on each resignation.
 */
@RestController
@RequestMapping("/api/resignations")
@PreAuthorize("isAuthenticated()")
public class ResignationController {

    private final ResignationService service;

    public ResignationController(ResignationService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ResignationService.MyView mine() {
        return service.mine();
    }

    @PostMapping("/me")
    public ResignationService.MyView submit(@RequestBody ResignationService.SubmitRequest request) {
        return service.submit(request);
    }

    @PostMapping("/me/{id}/withdraw")
    public ResignationService.MyView withdraw(@PathVariable Long id) {
        return service.withdraw(id);
    }

    @PostMapping("/me/{id}/exit-interview")
    public ResignationService.MyView exitInterview(@PathVariable Long id, @RequestBody Map<String, Object> answers) {
        return service.submitInterview(id, answers);
    }

    @GetMapping
    public List<ResignationService.ResignationDTO> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @GetMapping("/pending")
    public List<ResignationService.ResignationDTO> pending() {
        return service.pendingForMe();
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public ResignationService.Summary summary() {
        return service.summary();
    }

    @GetMapping("/settings")
    public ResignationService.SettingsDTO settings() {
        return service.settings();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public ResignationService.SettingsDTO updateSettings(@RequestBody ResignationService.SettingsDTO request) {
        return service.updateSettings(request);
    }

    @GetMapping("/{id}")
    public ResignationService.ResignationDTO get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve")
    public ResignationService.ResignationDTO approve(@PathVariable Long id, @RequestBody(required = false) ResignationService.ReviewRequest body,
                                                     @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.review(id, true, body, name(principal));
    }

    @PostMapping("/{id}/retain")
    public ResignationService.ResignationDTO retain(@PathVariable Long id, @RequestBody(required = false) ResignationService.ReviewRequest body,
                                                    @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.review(id, false, body, name(principal));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public ResignationService.ResignationDTO cancel(@PathVariable Long id, @RequestBody(required = false) ResignationService.ReviewRequest body,
                                                    @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.cancel(id, body == null ? null : body.note(), name(principal));
    }

    private static String name(CustomUserPrincipal principal) {
        if (principal == null || principal.getUser() == null) return "Reviewer";
        String full = principal.getUser().getFullName();
        return full == null || full.isBlank() ? principal.getUsername() : full;
    }
}
