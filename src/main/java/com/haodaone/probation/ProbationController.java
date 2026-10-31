package com.haodaone.probation;

import com.haodaone.security.CustomUserPrincipal;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Probation and confirmation. HR (EMPLOYEE_MANAGE org-wide) runs it; reporting managers recommend; employees see their own. */
@RestController
@RequestMapping("/api/probation")
@PreAuthorize("isAuthenticated()")
public class ProbationController {

    private final ProbationService service;

    public ProbationController(ProbationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProbationService.ProbationDTO> list() { return service.list(); }

    @GetMapping("/me")
    public ProbationService.ProbationDTO mine() { return service.mine(); }

    @GetMapping("/settings")
    public ProbationService.SettingsView settings() { return service.settings(); }

    @PutMapping("/settings")
    public ProbationService.SettingsView saveSettings(@RequestBody ProbationService.SettingsView v, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.saveSettings(v, name(p));
    }

    @PostMapping
    public ProbationService.ProbationDTO start(@RequestBody ProbationService.StartRequest r, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.start(r, name(p));
    }

    @PostMapping("/{id}/recommend")
    public ProbationService.ProbationDTO recommend(@PathVariable Long id, @RequestBody ProbationService.RecommendRequest r) {
        return service.recommend(id, r);
    }

    @PostMapping("/{id}/decide")
    public ProbationService.ProbationDTO decide(@PathVariable Long id, @RequestBody ProbationService.DecideRequest r, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.decide(id, r, name(p));
    }

    static String name(CustomUserPrincipal principal) {
        if (principal == null || principal.getUser() == null) return "HR";
        String full = principal.getUser().getFullName();
        return full == null || full.isBlank() ? principal.getUsername() : full;
    }

    /** 07:30 IST daily: enroll new joiners and open confirmation reviews that are due. */
    @Component
    static class ProbationScheduler {
        private final ProbationService service;
        private final Clock clock;

        ProbationScheduler(ProbationService service, Clock applicationClock) {
            this.service = service;
            this.clock = applicationClock;
        }

        @Scheduled(cron = "0 30 7 * * *", zone = "Asia/Kolkata")
        void run() {
            service.runDaily(LocalDate.now(clock));
        }
    }
}
