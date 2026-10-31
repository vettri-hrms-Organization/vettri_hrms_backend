package com.haodaone.salary.bonus;

import com.haodaone.security.CustomUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Bonus runs (payroll admins with organization-wide SALARY_MANAGE). */
@RestController
@RequestMapping("/api/salary/bonuses")
@PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
public class BonusController {

    private final BonusService service;

    public BonusController(BonusService service) {
        this.service = service;
    }

    @GetMapping
    public List<BonusService.BatchDTO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public BonusService.BatchDTO get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public BonusService.BatchDTO create(@RequestBody BonusService.CreateRequest request, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.create(request, name(principal));
    }

    @PutMapping("/{id}/lines/{lineId}")
    public BonusService.BatchDTO updateLine(@PathVariable Long id, @PathVariable Long lineId, @RequestBody BonusService.LineUpdate body) {
        return service.updateLine(id, lineId, body);
    }

    @PostMapping("/{id}/approve")
    public BonusService.BatchDTO approve(@PathVariable Long id, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.approve(id, name(principal));
    }

    @PostMapping("/{id}/cancel")
    public BonusService.BatchDTO cancel(@PathVariable Long id, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.cancel(id, name(principal));
    }

    private static String name(CustomUserPrincipal principal) {
        if (principal == null || principal.getUser() == null) return "Payroll admin";
        String full = principal.getUser().getFullName();
        return full == null || full.isBlank() ? principal.getUsername() : full;
    }
}
