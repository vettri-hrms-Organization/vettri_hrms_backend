package com.haodaone.recruitment.controller;

import com.haodaone.recruitment.dto.InterviewDTO;
import com.haodaone.recruitment.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping("/candidate/{candidateId}")
    @PreAuthorize("hasAuthority('RECRUITMENT_VIEW')")
    public List<InterviewDTO> byCandidate(@PathVariable Long candidateId) {
        return interviewService.byCandidate(candidateId);
    }

    @GetMapping("/upcoming")
    @PreAuthorize("hasAuthority('RECRUITMENT_VIEW')")
    public List<InterviewDTO> upcoming() {
        return interviewService.upcoming();
    }

    /** Manager Portal: "My Interviews" / "Assigned Interviews" - self-scoped to the caller, so any authenticated user can call it (there's simply nothing to see if you're not an assigned interviewer). */
    @GetMapping("/my")
    public List<InterviewDTO> myInterviews() {
        return interviewService.myInterviews();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public ResponseEntity<InterviewDTO> schedule(@Valid @RequestBody InterviewDTO.CreateRequest request) {
        return ResponseEntity.status(201).body(interviewService.schedule(request));
    }

    @PatchMapping("/{id}/feedback")
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public InterviewDTO submitFeedback(@PathVariable Long id, @Valid @RequestBody InterviewDTO.FeedbackRequest request) {
        return interviewService.submitFeedback(id, request);
    }

    /** Re-sends the manager/candidate assignment emails for an already-scheduled interview - see the "Resend Invite" button on the candidate detail view. */
    @PostMapping("/{id}/resend-invite")
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public ResponseEntity<Void> resendInvite(@PathVariable Long id, @RequestBody(required = false) InterviewDTO.ResendInviteRequest request) {
        InterviewDTO.ResendInviteRequest body = request != null ? request : new InterviewDTO.ResendInviteRequest();
        interviewService.resendInvite(id, body.isToManager(), body.isToCandidate());
        return ResponseEntity.noContent().build();
    }

    /**
     * Manager/final-round decision (technical/communication/overall
     * rating + remarks + Reject/Select-for-Final/Approve-for-Offer).
     * Broader than RECRUITMENT_MANAGE on purpose - a plain manager with
     * only INTERVIEW_DECISION can call this, but InterviewService further
     * restricts them to interviews they're actually assigned to.
     */
    @PatchMapping("/{id}/decision")
    @PreAuthorize("hasAnyAuthority('RECRUITMENT_MANAGE', 'INTERVIEW_DECISION')")
    public InterviewDTO submitDecision(@PathVariable Long id, @Valid @RequestBody InterviewDTO.DecisionRequest request) {
        return interviewService.submitDecision(id, request);
    }
}
