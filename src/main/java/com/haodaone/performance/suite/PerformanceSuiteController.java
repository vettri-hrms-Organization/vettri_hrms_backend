package com.haodaone.performance.suite;

import com.haodaone.security.CustomUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Review cycles, OKRs, 1:1s, feedback, PIPs and compensation planning. Every signed-in employee takes part, so the
 * endpoints only require authentication; the services check HR (PERFORMANCE_MANAGE / SALARY_MANAGE at organization
 * scope), reporting-manager and owner rules on each record.
 */
@RestController
@RequestMapping("/api/performance")
@PreAuthorize("isAuthenticated()")
public class PerformanceSuiteController {

    private final ReviewCycleService reviews;
    private final OkrService okrs;
    private final ConversationService conversations;
    private final PipService pips;
    private final CompPlanningService comp;
    private final PerfContext ctx;

    public PerformanceSuiteController(ReviewCycleService reviews, OkrService okrs, ConversationService conversations, PipService pips,
                                      CompPlanningService comp, PerfContext ctx) {
        this.ctx = ctx;
        this.reviews = reviews;
        this.okrs = okrs;
        this.conversations = conversations;
        this.pips = pips;
        this.comp = comp;
    }

    // ---------------------------------------------------------------- context

    /** Who the caller is in the performance suite: HR, payroll admin, and their direct reports. */
    @GetMapping("/me")
    public PerfContext.Me me() { return ctx.context(); }

    /** Colleague picker for feedback, peers and 1:1s: names and roles only, nothing personal. */
    @GetMapping("/people")
    public List<PerfContext.Person> people() { return ctx.directory(); }

    // ---------------------------------------------------------------- review cycles

    @GetMapping("/cycles")
    public List<ReviewCycleService.CycleDTO> cycles() { return reviews.cycles(); }

    @PostMapping("/cycles")
    public ReviewCycleService.CycleDTO createCycle(@RequestBody ReviewCycleService.CycleRequest r, @AuthenticationPrincipal CustomUserPrincipal p) { return reviews.create(r, name(p)); }

    @PostMapping("/cycles/{id}/advance")
    public ReviewCycleService.CycleDTO advance(@PathVariable Long id, @AuthenticationPrincipal CustomUserPrincipal p) { return reviews.advance(id, name(p)); }

    @PostMapping("/cycles/{id}/release")
    public ReviewCycleService.CycleDTO release(@PathVariable Long id, @AuthenticationPrincipal CustomUserPrincipal p) { return reviews.release(id, name(p)); }

    @GetMapping("/cycles/{id}/participants")
    public List<ReviewCycleService.ParticipantDTO> participants(@PathVariable Long id) { return reviews.participantsOf(id); }

    @PostMapping("/participants/{id}/calibrate")
    public ReviewCycleService.ParticipantDTO calibrate(@PathVariable Long id, @RequestBody Map<String, Object> body, @AuthenticationPrincipal CustomUserPrincipal p) {
        Object rating = body.get("finalRating");
        return reviews.calibrate(id, rating == null ? null : new BigDecimal(rating.toString()), (String) body.get("note"), name(p));
    }

    @PostMapping("/participants/{id}/peers")
    public ReviewCycleService.ParticipantDTO peers(@PathVariable Long id, @RequestBody List<Long> peerIds) { return reviews.addPeers(id, peerIds); }

    @PostMapping("/participants/{id}/acknowledge")
    public ReviewCycleService.ParticipantDTO acknowledgeReview(@PathVariable Long id) { return reviews.acknowledge(id); }

    @GetMapping("/reviews/tasks")
    public List<ReviewCycleService.TaskDTO> tasks() { return reviews.myTasks(); }

    @PostMapping("/reviews/{responseId}")
    public ReviewCycleService.TaskDTO submit(@PathVariable Long responseId, @RequestBody ReviewCycleService.ReviewBody body) { return reviews.submit(responseId, body); }

    @GetMapping("/reviews/mine")
    public List<ReviewCycleService.ParticipantDTO> myReviews() { return reviews.myResults(); }

    // ---------------------------------------------------------------- OKRs

    @GetMapping("/okrs")
    public List<OkrService.ObjectiveDTO> okrs(@RequestParam String period) { return okrs.list(period); }

    @PostMapping("/okrs")
    public OkrService.ObjectiveDTO createOkr(@RequestBody OkrService.ObjectiveRequest r) { return okrs.create(r); }

    @PutMapping("/okrs/{id}")
    public OkrService.ObjectiveDTO updateOkr(@PathVariable Long id, @RequestBody OkrService.ObjectiveRequest r) { return okrs.update(id, r); }

    @PostMapping("/okrs/{id}/status")
    public OkrService.ObjectiveDTO okrStatus(@PathVariable Long id, @RequestBody Map<String, String> body) { return okrs.setStatus(id, body.get("status")); }

    @DeleteMapping("/okrs/{id}")
    public void deleteOkr(@PathVariable Long id) { okrs.delete(id); }

    @PostMapping("/okrs/{id}/key-results")
    public OkrService.ObjectiveDTO addKeyResult(@PathVariable Long id, @RequestBody OkrService.KeyResultRequest r) { return okrs.addKeyResult(id, r); }

    @DeleteMapping("/key-results/{id}")
    public OkrService.ObjectiveDTO removeKeyResult(@PathVariable Long id) { return okrs.removeKeyResult(id); }

    @PostMapping("/key-results/{id}/check-in")
    public OkrService.ObjectiveDTO checkIn(@PathVariable Long id, @RequestBody OkrService.CheckIn r) { return okrs.checkIn(id, r); }

    // ---------------------------------------------------------------- 1:1s and feedback

    @GetMapping("/one-on-ones")
    public List<ConversationService.MeetingDTO> meetings() { return conversations.myMeetings(); }

    @PostMapping("/one-on-ones")
    public ConversationService.MeetingDTO schedule(@RequestBody ConversationService.MeetingRequest r) { return conversations.schedule(r); }

    @PutMapping("/one-on-ones/{id}")
    public ConversationService.MeetingDTO updateMeeting(@PathVariable Long id, @RequestBody ConversationService.MeetingUpdate u) { return conversations.update(id, u); }

    @PostMapping("/one-on-ones/{id}/status")
    public ConversationService.MeetingDTO meetingStatus(@PathVariable Long id, @RequestBody Map<String, String> body) { return conversations.setStatus(id, body.get("status")); }

    @PostMapping("/feedback")
    public ConversationService.FeedbackDTO give(@RequestBody ConversationService.FeedbackRequest r) { return conversations.give(r); }

    @GetMapping("/feedback/wall")
    public List<ConversationService.FeedbackDTO> wall() { return conversations.wall(); }

    @GetMapping("/feedback/received")
    public List<ConversationService.FeedbackDTO> received(@RequestParam(required = false) Long employeeId) { return conversations.received(employeeId); }

    @GetMapping("/feedback/given")
    public List<ConversationService.FeedbackDTO> given() { return conversations.given(); }

    // ---------------------------------------------------------------- PIPs

    @GetMapping("/pips")
    public List<PipService.PipDTO> pips() { return pips.list(); }

    @GetMapping("/pips/mine")
    public List<PipService.PipDTO> myPips() { return pips.mine(); }

    @PostMapping("/pips")
    public PipService.PipDTO createPip(@RequestBody PipService.PipRequest r, @AuthenticationPrincipal CustomUserPrincipal p) { return pips.create(r, name(p)); }

    @PostMapping("/pips/{id}/check-in")
    public PipService.PipDTO pipCheckIn(@PathVariable Long id, @RequestBody PipService.CheckInRequest r) { return pips.checkIn(id, r); }

    @PostMapping("/pips/{id}/close")
    public PipService.PipDTO closePip(@PathVariable Long id, @RequestBody PipService.CloseRequest r, @AuthenticationPrincipal CustomUserPrincipal p) { return pips.close(id, r, name(p)); }

    @PostMapping("/pips/{id}/acknowledge")
    public PipService.PipDTO acknowledgePip(@PathVariable Long id) { return pips.acknowledge(id); }

    // ---------------------------------------------------------------- compensation planning

    @GetMapping("/compensation")
    public List<CompPlanningService.CycleDTO> compCycles() { return comp.list(); }

    @GetMapping("/compensation/{id}")
    public CompPlanningService.CycleDTO compCycle(@PathVariable Long id) { return comp.get(id); }

    @PostMapping("/compensation")
    public CompPlanningService.CycleDTO createComp(@RequestBody CompPlanningService.CycleRequest r, @AuthenticationPrincipal CustomUserPrincipal p) { return comp.create(r, name(p)); }

    @PostMapping("/compensation/{id}/regenerate")
    public CompPlanningService.CycleDTO regenerate(@PathVariable Long id, @RequestBody(required = false) Map<String, BigDecimal> guidelines) { return comp.regenerate(id, guidelines); }

    @PutMapping("/compensation/{id}/recommendations/{recId}")
    public CompPlanningService.CycleDTO updateRec(@PathVariable Long id, @PathVariable Long recId, @RequestBody CompPlanningService.RecommendationUpdate u) {
        return comp.updateRecommendation(id, recId, u);
    }

    @PostMapping("/compensation/{id}/apply")
    public CompPlanningService.ApplyResult applyComp(@PathVariable Long id, @AuthenticationPrincipal CustomUserPrincipal p) { return comp.apply(id, name(p)); }

    private static String name(CustomUserPrincipal principal) {
        if (principal == null || principal.getUser() == null) return "HR";
        String full = principal.getUser().getFullName();
        return full == null || full.isBlank() ? principal.getUsername() : full;
    }
}
