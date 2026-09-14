package com.haodaone.recruitment.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.recruitment.dto.InterviewDTO;
import com.haodaone.recruitment.entity.Candidate;
import com.haodaone.recruitment.entity.Interview;
import com.haodaone.recruitment.repository.CandidateRepository;
import com.haodaone.recruitment.repository.InterviewRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class InterviewService {

    /** Round number -> the candidate stage that round belongs to, and the human-readable round type label. */
    private static final Map<Integer, String> ROUND_STAGE = Map.of(1, "ROUND1", 2, "ROUND2", 3, "ROUND3");
    private static final Map<Integer, String> ROUND_TYPE = Map.of(1, "HR_INTERVIEW", 2, "HIRING_MANAGER", 3, "FINAL");

    private final InterviewRepository interviewRepository;
    private final CandidateRepository candidateRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    public InterviewService(InterviewRepository interviewRepository, CandidateRepository candidateRepository,
                             EmployeeRepository employeeRepository, AuditLogService auditLogService,
                             EmailService emailService) {
        this.interviewRepository = interviewRepository;
        this.candidateRepository = candidateRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogService = auditLogService;
        this.emailService = emailService;
    }

    /**
     * @Transactional(readOnly = true) is required: InterviewDTO.from() calls
     * candidate.getFullName() and interviewer.getFullName(), both lazy
     * relations (see CandidateService.listAll()'s javadoc for the full
     * explanation of why this throws without an open session).
     */
    @Transactional(readOnly = true)
    public List<InterviewDTO> byCandidate(Long candidateId) {
        return interviewRepository.findAllByCandidateIdAndDeletedFalseOrderByScheduledAtDesc(candidateId).stream()
                .map(InterviewDTO::from)
                .toList();
    }

    /** Same lazy-relation issue as byCandidate() above. */
    @Transactional(readOnly = true)
    public List<InterviewDTO> upcoming() {
        return interviewRepository.findAllByStatusOrderByScheduledAtAsc("SCHEDULED").stream()
                .map(InterviewDTO::from)
                .toList();
    }

    /**
     * Manager Portal: interviews assigned to the currently logged-in user,
     * resolved via their linked Employee record.
     *
     * @Transactional(readOnly = true) is required here even more than the
     * other two methods above: fromWithCandidateContext() reaches through
     * candidate.getJobOpening().getTitle() - two lazy hops deep - so this
     * is the method most likely to blow up without an open session.
     */
    @Transactional(readOnly = true)
    public List<InterviewDTO> myInterviews() {
        Employee me = currentEmployee()
                .orElseThrow(() -> new BadRequestException("Your account isn't linked to an employee profile, so you have no assigned interviews."));
        return interviewRepository.findAllByInterviewer_IdAndDeletedFalseOrderByScheduledAtDesc(me.getId()).stream()
                .map(InterviewDTO::fromWithCandidateContext)
                .toList();
    }

    /**
     * Re-sends the assignment emails for an already-scheduled interview -
     * to the manager, the candidate, or both - without HR needing to dig
     * up and re-paste the Google Meet link by hand. Reuses the exact same
     * EmailService methods assignManagerRound() calls when the interview
     * is first created, so the resend is identical in content to the
     * original invite (just re-triggered).
     */
    @Transactional(readOnly = true)
    public void resendInvite(Long id, boolean toManager, boolean toCandidate) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + id));

        if (interview.getMeetingLink() == null || interview.getMeetingLink().isBlank()) {
            throw new BadRequestException("This interview has no Google Meet link on file to resend.");
        }
        if (!toManager && !toCandidate) {
            throw new BadRequestException("Choose at least one recipient to resend the invite to.");
        }
        if (interview.getInterviewer() == null) {
            throw new BadRequestException("This interview has no assigned interviewer to resend to.");
        }

        Candidate candidate = interview.getCandidate();
        Employee interviewer = interview.getInterviewer();

        if (toManager) {
            emailService.sendManagerAssignmentEmail(candidate, interview, interviewer);
        }
        if (toCandidate) {
            emailService.sendCandidateManagerRoundEmail(candidate, interview, interviewer);
        }

        auditLogService.log("Interview", interview.getId(), "INVITE_RESENT",
                "Resent round " + interview.getRoundNumber() + " invite for '" + candidate.getFullName() + "'"
                        + " to" + (toManager ? " manager" : "") + (toCandidate ? " candidate" : ""));
    }

    /**
     * Scheduling a round's interview requires the candidate to already be
     * in that round's stage (ROUND1/ROUND2/ROUND3) - i.e. HR must have
     * deliberately advanced them there first via CandidateService.advance.
     * This keeps "which round is the candidate in" and "log an interview
     * event for that round" as two separate, explicit HR actions rather
     * than inferring stage from whichever interview happens to get
     * scheduled.
     */
    @Transactional
    public InterviewDTO schedule(InterviewDTO.CreateRequest request) {
        Candidate candidate = candidateRepository.findById(request.getCandidateId())
                .orElseThrow(() -> new BadRequestException("Unknown candidate: " + request.getCandidateId()));

        String requiredStage = ROUND_STAGE.get(request.getRoundNumber());
        if (requiredStage == null) {
            throw new BadRequestException("Round must be 1, 2, or 3.");
        }
        if (!requiredStage.equals(candidate.getStage())) {
            throw new BadRequestException("Move the candidate to " + requiredStage
                    + " before scheduling a Round " + request.getRoundNumber() + " interview (currently " + candidate.getStage() + ").");
        }

        Interview interview = new Interview();
        interview.setCandidate(candidate);
        interview.setScheduledAt(request.getScheduledAt());
        interview.setRoundNumber(request.getRoundNumber());
        interview.setRoundType(ROUND_TYPE.get(request.getRoundNumber()));
        interview.setMode(request.getMode() != null ? request.getMode() : "VIDEO");
        interview.setStatus("SCHEDULED");

        if (request.getInterviewerId() != null) {
            Employee interviewer = employeeRepository.findById(request.getInterviewerId())
                    .orElseThrow(() -> new BadRequestException("Unknown interviewer: " + request.getInterviewerId()));
            interview.setInterviewer(interviewer);
        }

        Interview saved = interviewRepository.save(interview);
        auditLogService.log("Interview", saved.getId(), "CREATE",
                "Scheduled Round " + request.getRoundNumber() + " interview for '" + candidate.getFullName() + "' at " + request.getScheduledAt());
        return InterviewDTO.from(saved);
    }

    @Transactional
    public InterviewDTO submitFeedback(Long id, InterviewDTO.FeedbackRequest request) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + id));

        interview.setRating(request.getRating());
        interview.setFeedback(request.getFeedback());
        interview.setStatus("COMPLETED");

        Interview saved = interviewRepository.save(interview);
        auditLogService.log("Interview", saved.getId(), "UPDATE",
                "Round " + interview.getRoundNumber() + " feedback submitted (rating " + request.getRating() + "/5)");
        return InterviewDTO.from(saved);
    }

    /**
     * The manager-round or final-round interviewer's post-interview
     * decision: technical/communication/overall ratings, remarks, and a
     * decision that's either terminal (REJECTED) or advances the
     * candidate to the next stage. Valid decisions depend on the round:
     * round 2 -> REJECTED or SELECT_FOR_FINAL (candidate moves to
     * ROUND3, where HR schedules the final interview same as any other
     * round); round 3 -> REJECTED or APPROVED_FOR_OFFER (candidate stays
     * ROUND3, which is exactly the stage CandidateService.generateOffer
     * already requires - HR sees the update and generates the offer from
     * there, reusing that existing action rather than a new one).
     *
     * Authorization is resource-scoped rather than a blanket permission:
     * whoever has RECRUITMENT_MANAGE (HR/admin) can submit on any
     * interview, but a plain manager (INTERVIEW_DECISION only, no
     * RECRUITMENT_MANAGE) can only submit on interviews where they are
     * the assigned interviewer - enforced here, not just at the
     * controller, since @PreAuthorize can't express "only your own rows"
     * without a lookup this service already has to do anyway.
     */
    @Transactional
    public InterviewDTO submitDecision(Long id, InterviewDTO.DecisionRequest request) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + id));

        if (interview.getRoundNumber() != 2 && interview.getRoundNumber() != 3) {
            throw new BadRequestException("Only manager-round (2) and final-round (3) interviews take a decision here - use the feedback endpoint for round 1.");
        }
        assertCanDecide(interview);

        Set<String> validDecisions = interview.getRoundNumber() == 2
                ? Set.of("REJECTED", "SELECT_FOR_FINAL")
                : Set.of("REJECTED", "APPROVED_FOR_OFFER");
        if (!validDecisions.contains(request.getDecision())) {
            throw new BadRequestException("Round " + interview.getRoundNumber() + " decision must be one of " + validDecisions);
        }

        interview.setTechnicalRating(request.getTechnicalRating());
        interview.setCommunicationRating(request.getCommunicationRating());
        interview.setRating(request.getOverallRating());
        interview.setFeedback(request.getRemarks());
        interview.setDecision(request.getDecision());
        interview.setStatus("COMPLETED");
        Interview savedInterview = interviewRepository.save(interview);

        Candidate candidate = interview.getCandidate();
        String fromStage = candidate.getStage();
        if ("REJECTED".equals(request.getDecision())) {
            candidate.setStage("REJECTED");
        } else if ("SELECT_FOR_FINAL".equals(request.getDecision())) {
            candidate.setStage("ROUND3");
        }
        // APPROVED_FOR_OFFER leaves the candidate at ROUND3 on purpose - see javadoc above.
        Candidate savedCandidate = candidateRepository.save(candidate);

        auditLogService.log("Interview", savedInterview.getId(), "DECISION",
                "Round " + interview.getRoundNumber() + " decision for '" + savedCandidate.getFullName() + "': "
                        + request.getDecision() + " (overall " + request.getOverallRating() + "/5)"
                        + (!fromStage.equals(savedCandidate.getStage()) ? " [" + fromStage + " -> " + savedCandidate.getStage() + "]" : ""));

        return InterviewDTO.from(savedInterview);
    }

    private void assertCanDecide(Interview interview) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }
        boolean hasFullManageAuthority = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("RECRUITMENT_MANAGE"::equals);
        if (hasFullManageAuthority) {
            return;
        }
        Employee me = currentEmployee().orElse(null);
        boolean isAssignedInterviewer = me != null && interview.getInterviewer() != null
                && me.getId().equals(interview.getInterviewer().getId());
        if (!isAssignedInterviewer) {
            throw new AccessDeniedException("You can only submit a decision for interviews assigned to you.");
        }
    }

    private Optional<Employee> currentEmployee() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return employeeRepository.findByUser_UsernameAndDeletedFalse(authentication.getName());
    }
}
