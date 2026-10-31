package com.haodaone.survey;

import com.haodaone.security.CustomUserPrincipal;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Surveys, polls and eNPS. HR (EMPLOYEE_MANAGE org-wide) manages them; every employee answers the ones meant for them. */
@RestController
@RequestMapping("/api/surveys")
@PreAuthorize("isAuthenticated()")
public class SurveyController {

    private final SurveyService service;

    public SurveyController(SurveyService service) {
        this.service = service;
    }

    @GetMapping
    public List<SurveyService.SurveyDTO> list() { return service.list(); }

    @GetMapping("/mine")
    public List<SurveyService.SurveyDTO> mine() { return service.forMe(); }

    @PostMapping
    public SurveyService.SurveyDTO create(@RequestBody SurveyService.SurveyRequest r, @AuthenticationPrincipal CustomUserPrincipal p) { return service.save(null, r, name(p)); }

    @PutMapping("/{id}")
    public SurveyService.SurveyDTO update(@PathVariable Long id, @RequestBody SurveyService.SurveyRequest r, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.save(id, r, name(p));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { service.delete(id); }

    @PostMapping("/{id}/publish")
    public SurveyService.SurveyDTO publish(@PathVariable Long id, @AuthenticationPrincipal CustomUserPrincipal p) { return service.publish(id, name(p)); }

    @PostMapping("/{id}/close")
    public SurveyService.SurveyDTO close(@PathVariable Long id, @AuthenticationPrincipal CustomUserPrincipal p) { return service.close(id, name(p)); }

    @GetMapping("/{id}/results")
    public SurveyService.Results results(@PathVariable Long id) { return service.results(id); }

    @PostMapping("/{id}/answers")
    public SurveyService.SurveyDTO answer(@PathVariable Long id, @RequestBody Map<String, Object> answers) { return service.answer(id, answers); }

    private static String name(CustomUserPrincipal principal) {
        if (principal == null || principal.getUser() == null) return "HR";
        String full = principal.getUser().getFullName();
        return full == null || full.isBlank() ? principal.getUsername() : full;
    }

    @Component
    static class SurveyAutoClose {
        private final SurveyService service;
        private final Clock clock;

        SurveyAutoClose(SurveyService service, Clock applicationClock) {
            this.service = service;
            this.clock = applicationClock;
        }

        @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Kolkata")
        void run() {
            service.closeExpired(LocalDate.now(clock));
        }
    }
}
