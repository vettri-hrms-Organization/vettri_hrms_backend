package com.haodaone.helpdesk;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** HR helpdesk: employees raise and follow their tickets; HR and category owners work the queue. */
@RestController
@RequestMapping("/api/helpdesk")
@PreAuthorize("isAuthenticated()")
public class HelpdeskController {

    private final HelpdeskService service;

    public HelpdeskController(HelpdeskService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public List<HelpdeskService.CategoryDTO> categories() { return service.categories(); }

    @PostMapping("/categories")
    public List<HelpdeskService.CategoryDTO> createCategory(@RequestBody HelpdeskService.CategoryRequest r) { return service.saveCategory(null, r); }

    @PutMapping("/categories/{id}")
    public List<HelpdeskService.CategoryDTO> updateCategory(@PathVariable Long id, @RequestBody HelpdeskService.CategoryRequest r) { return service.saveCategory(id, r); }

    @GetMapping("/tickets/mine")
    public List<HelpdeskService.TicketDTO> mine() { return service.mine(); }

    @PostMapping("/tickets")
    public HelpdeskService.TicketDTO raise(@RequestBody HelpdeskService.TicketRequest r) { return service.raise(r); }

    @GetMapping("/tickets")
    public List<HelpdeskService.TicketDTO> queue(@RequestParam(required = false) String status) { return service.queue(status); }

    @GetMapping("/summary")
    public HelpdeskService.Summary summary() { return service.summary(); }

    @GetMapping("/tickets/{id}")
    public HelpdeskService.TicketDTO get(@PathVariable Long id) { return service.get(id); }

    @PostMapping("/tickets/{id}/comments")
    public HelpdeskService.TicketDTO comment(@PathVariable Long id, @RequestBody HelpdeskService.CommentRequest r) { return service.comment(id, r); }

    @PatchMapping("/tickets/{id}")
    public HelpdeskService.TicketDTO update(@PathVariable Long id, @RequestBody HelpdeskService.UpdateRequest r) { return service.update(id, r); }

    @PostMapping("/tickets/{id}/rate")
    public HelpdeskService.TicketDTO rate(@PathVariable Long id, @RequestBody HelpdeskService.RatingRequest r) { return service.rate(id, r); }

    @Component
    static class HelpdeskAutoClose {
        private final HelpdeskService service;
        private final Clock clock;

        HelpdeskAutoClose(HelpdeskService service, Clock applicationClock) {
            this.service = service;
            this.clock = applicationClock;
        }

        @Scheduled(cron = "0 15 2 * * *", zone = "Asia/Kolkata")
        void run() {
            service.autoClose(LocalDateTime.now(clock));
        }
    }
}
