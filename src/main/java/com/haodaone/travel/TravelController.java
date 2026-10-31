package com.haodaone.travel;

import com.haodaone.security.CustomUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Travel requests and advances. The service checks owner / reporting manager / finance (EXPENSE_MANAGE org-wide). */
@RestController
@RequestMapping("/api/travel")
@PreAuthorize("isAuthenticated()")
public class TravelController {

    private final TravelService service;

    public TravelController(TravelService service) {
        this.service = service;
    }

    @GetMapping("/mine")
    public List<TravelService.TripDTO> mine() { return service.mine(); }

    @PostMapping
    public TravelService.TripDTO create(@RequestBody TravelService.TripRequest r) { return service.create(r); }

    @PostMapping("/{id}/cancel")
    public TravelService.TripDTO cancel(@PathVariable Long id) { return service.cancel(id); }

    @GetMapping
    public List<TravelService.TripDTO> queue(@RequestParam(required = false) String status) { return service.queue(status); }

    @PostMapping("/{id}/decision")
    public TravelService.TripDTO decide(@PathVariable Long id, @RequestBody TravelService.DecisionRequest r, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.decide(id, r, name(p));
    }

    @PostMapping("/{id}/advance/pay")
    public TravelService.TripDTO pay(@PathVariable Long id, @RequestBody Map<String, String> body, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.payAdvance(id, body.get("reference"), name(p));
    }

    @PostMapping("/{id}/settle")
    public TravelService.TripDTO settle(@PathVariable Long id, @RequestBody TravelService.SettleRequest r, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.settle(id, r, name(p));
    }

    private static String name(CustomUserPrincipal principal) {
        if (principal == null || principal.getUser() == null) return "Approver";
        String full = principal.getUser().getFullName();
        return full == null || full.isBlank() ? principal.getUsername() : full;
    }
}
