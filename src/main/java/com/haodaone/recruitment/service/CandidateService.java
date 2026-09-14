package com.haodaone.recruitment.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.dto.CreateEmployeeRequest;
import com.haodaone.employee.dto.EmployeeDetailDTO;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.employee.service.EmployeeService;
import com.haodaone.recruitment.dto.CandidateDTO;
import com.haodaone.recruitment.entity.Candidate;
import com.haodaone.recruitment.entity.Interview;
import com.haodaone.recruitment.entity.JobOpening;
import com.haodaone.recruitment.repository.CandidateRepository;
import com.haodaone.recruitment.repository.InterviewRepository;
import com.haodaone.recruitment.repository.JobOpeningRepository;
import com.haodaone.employee.service.EmployeeInvitationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.haodaone.tenant.TenantContext;
import java.util.Map;
import java.util.Set;

@Service
public class CandidateService {

    /** Full pipeline vocabulary - see Candidate's class doc for the stage diagram. */
    private static final Set<String> VALID_STAGES = Set.of(
            "APPLIED", "SHORTLISTED", "HOLD", "ROUND1", "ROUND2", "ROUND3", "OFFERED", "OFFER_LETTER_SENT", "HIRED", "REJECTED");

    /** Stages an offer letter can be uploaded/replaced or (re)sent during. */
    private static final Set<String> OFFER_LETTER_STAGES = Set.of("OFFERED", "OFFER_LETTER_SENT");

    /** Decisions the initial HR review (on an APPLIED candidate) can make. */
    private static final Set<String> REVIEW_DECISIONS = Set.of("SHORTLISTED", "HOLD", "REJECTED");

    /**
     * Every other controlled transition. REJECTED is reachable from any
     * key here (enforced separately below, not repeated in every set) -
     * this map only needs to list the "positive" moves.
     */
    private static final Map<String, Set<String>> ALLOWED_ADVANCES = Map.of(
            "SHORTLISTED", Set.of("ROUND1", "HOLD"),
            "HOLD", Set.of("SHORTLISTED", "ROUND1", "ROUND2", "ROUND3"),
            "ROUND1", Set.of("ROUND2", "HOLD"),
            "ROUND2", Set.of("ROUND3", "HOLD"),
            "ROUND3", Set.of("HOLD"));

    private static final Set<String> TERMINAL_STAGES = Set.of("HIRED", "REJECTED");

    private final CandidateRepository candidateRepository;
    private final JobOpeningRepository jobOpeningRepository;
    private final ResumeS3StorageService resumeStorageService;
    private final OfferLetterS3StorageService offerLetterStorageService;
    private final EmployeeService employeeService;
    private final AuditLogService auditLogService;
    private final InterviewRepository interviewRepository;
    private final EmployeeRepository employeeRepository;
    private final EmailService emailService;
    private final EmployeeInvitationService employeeInvitationService;

    public CandidateService(CandidateRepository candidateRepository, JobOpeningRepository jobOpeningRepository,
                             ResumeS3StorageService resumeStorageService, OfferLetterS3StorageService offerLetterStorageService,
                             EmployeeService employeeService,
                             AuditLogService auditLogService, InterviewRepository interviewRepository,
                             EmployeeRepository employeeRepository, EmailService emailService,
                             EmployeeInvitationService employeeInvitationService) {
        this.candidateRepository = candidateRepository;
        this.jobOpeningRepository = jobOpeningRepository;
        this.resumeStorageService = resumeStorageService;
        this.offerLetterStorageService = offerLetterStorageService;
        this.employeeService = employeeService;
        this.auditLogService = auditLogService;
        this.interviewRepository = interviewRepository;
        this.employeeRepository = employeeRepository;
        this.emailService = emailService;
        this.employeeInvitationService = employeeInvitationService;
    }

    /**
     * @Transactional(readOnly = true) is required here, not optional: with
     * spring.jpa.open-in-view=false (correctly set - see application.properties),
     * the Hibernate session normally closes the instant the repository call
     * returns. Candidate.jobOpening is FetchType.LAZY, and CandidateDTO.from()
     * calls jobOpening.getTitle() - a real field, not the FK id, so Hibernate
     * must hit the DB to initialize that proxy. Without an open session at
     * that point, it throws LazyInitializationException, which
     * GlobalExceptionHandler's catch-all turns into the generic 500 "Something
     * went wrong" the frontend shows. Keeping the transaction open for the
     * whole method (including the DTO mapping) fixes that permanently.
     */
    @Transactional(readOnly = true)
    public List<CandidateDTO> listAll(Long jobOpeningId) {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        List<Candidate> candidates = jobOpeningId != null
            ? candidateRepository.findAllByJobOpening_Company_IdAndJobOpeningIdAndDeletedFalseOrderByAppliedDateDesc(tenant, jobOpeningId)
            : candidateRepository.findAllByJobOpening_Company_IdAndDeletedFalseOrderByAppliedDateDesc(tenant);
        return candidates.stream().map(CandidateDTO::from).toList();
    }

    /** See listAll()'s javadoc - same lazy-jobOpening issue applies here. */
    @Transactional(readOnly = true)
    public CandidateDTO getById(Long id) {
        return CandidateDTO.from(findActiveOrThrow(id));
    }

    /** Resume storage key isn't exposed on CandidateDTO (only hasResume/resumeOriginalName are) - the download endpoint needs the raw key. */
    public String getResumeKey(Long id) {
        return findActiveOrThrow(id).getResumeFileKey();
    }

    /** Manual add by HR (e.g. a referral) - no resume file, optionally a resumeUrl link. */
    @Transactional
    public CandidateDTO create(CandidateDTO.CreateRequest request) {
        JobOpening opening = jobOpeningRepository.findById(request.getJobOpeningId())
                .orElseThrow(() -> new BadRequestException("Unknown job opening: " + request.getJobOpeningId()));
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null || opening.getCompany() == null || !tenant.equals(opening.getCompany().getId())) throw new BadRequestException("Job opening is not in this company");

        Candidate candidate = new Candidate();
        candidate.setFirstName(request.getFirstName());
        candidate.setLastName(request.getLastName());
        candidate.setEmail(request.getEmail());
        candidate.setPhone(request.getPhone());
        candidate.setJobOpening(opening);
        candidate.setSource(request.getSource() != null ? request.getSource() : "Manual Entry");
        candidate.setResumeUrl(request.getResumeUrl());
        candidate.setExperienceYears(request.getExperienceYears());
        candidate.setSkills(request.getSkills());
        candidate.setNotes(request.getNotes());
        candidate.setAppliedDate(LocalDate.now());
        candidate.setStage("APPLIED");

        Candidate saved = candidateRepository.save(candidate);
        auditLogService.log("Candidate", saved.getId(), "CREATE",
                "Added '" + saved.getFullName() + "' to the pipeline for '" + opening.getTitle() + "'");
        return CandidateDTO.from(saved);
    }

    /**
     * Public, unauthenticated application from the Careers page. Rejects
     * applications to a job that isn't currently OPEN, even if the id is
     * still guessable/bookmarked from when it was.
     */
    @Transactional
    public CandidateDTO apply(CandidateDTO.PublicApplicationRequest request, MultipartFile resume) {
        JobOpening opening = jobOpeningRepository.findById(request.getJobOpeningId())
                .orElseThrow(() -> new BadRequestException("This job opening no longer exists."));
        if (!"OPEN".equals(opening.getStatus())) {
            throw new BadRequestException("This job opening is no longer accepting applications.");
        }

        Candidate candidate = new Candidate();
        candidate.setFirstName(request.getFirstName());
        candidate.setLastName(request.getLastName());
        candidate.setEmail(request.getEmail());
        candidate.setPhone(request.getPhone());
        candidate.setJobOpening(opening);
        candidate.setSource("Careers Page");
        candidate.setExperienceYears(request.getExperienceYears());
        candidate.setSkills(request.getSkills());
        candidate.setNotes(request.getNotes());
        candidate.setAppliedDate(LocalDate.now());
        candidate.setStage("APPLIED");

        if (resume != null && !resume.isEmpty()) {
            String key = resumeStorageService.store(resume);
            candidate.setResumeFileKey(key);
            candidate.setResumeOriginalName(resume.getOriginalFilename());
        }

        Candidate saved = candidateRepository.save(candidate);
        auditLogService.log("Candidate", saved.getId(), "CREATE",
                "'" + saved.getFullName() + "' applied via Careers page for '" + opening.getTitle() + "'");
        return CandidateDTO.from(saved);
    }

    /** HR's initial screening decision on an APPLIED candidate: shortlist, hold, or reject. */
    @Transactional
    public CandidateDTO review(Long id, CandidateDTO.ReviewRequest request) {
        if (!REVIEW_DECISIONS.contains(request.getDecision())) {
            throw new BadRequestException("Review decision must be one of " + REVIEW_DECISIONS);
        }
        Candidate candidate = findActiveOrThrow(id);
        if (!"APPLIED".equals(candidate.getStage())) {
            throw new BadRequestException("Only an APPLIED candidate can be reviewed - this candidate is already " + candidate.getStage() + ".");
        }

        applyDecision(candidate, request.getDecision(), request.getRating(), request.getRemarks(), request.getRejectionReason());

        Candidate saved = candidateRepository.save(candidate);
        auditLogService.log("Candidate", saved.getId(), "REVIEW",
                "'" + saved.getFullName() + "' reviewed -> " + request.getDecision()
                        + (request.getRating() != null ? " (rating " + request.getRating() + "/5)" : ""));
        return CandidateDTO.from(saved);
    }

    /**
     * Free-text recruiter notes. A single field on Candidate (not a
     * timestamped multi-entry history) - kept simple to match what
     * actually exists on the entity today rather than introducing a new
     * Note entity/table for what was, until now, an unwired field. Each
     * edit overwrites the previous text, same as the entity always
     * supported; the audit log entry is what preserves history of who
     * changed it and when, not a note-per-row schema.
     */
    @Transactional
    public CandidateDTO updateNotes(Long id, CandidateDTO.UpdateNotesRequest request) {
        Candidate candidate = findActiveOrThrow(id);
        candidate.setNotes(request.getNotes());
        Candidate saved = candidateRepository.save(candidate);
        auditLogService.log("Candidate", saved.getId(), "NOTES_UPDATED", "Notes updated for '" + saved.getFullName() + "'");
        return CandidateDTO.from(saved);
    }

    /** Round-by-round pipeline advancement (or hold/reject) once past the initial review. */
    @Transactional
    public CandidateDTO advance(Long id, CandidateDTO.AdvanceStageRequest request) {
        Candidate candidate = findActiveOrThrow(id);
        String from = candidate.getStage();
        String to = request.getTargetStage();

        if (!VALID_STAGES.contains(to)) {
            throw new BadRequestException("Unknown stage: " + to);
        }
        if (TERMINAL_STAGES.contains(from)) {
            throw new BadRequestException("Candidate is already " + from + " - no further pipeline changes are possible.");
        }
        boolean isRejection = "REJECTED".equals(to);
        boolean isAllowedAdvance = ALLOWED_ADVANCES.getOrDefault(from, Set.of()).contains(to);
        if (!isRejection && !isAllowedAdvance) {
            throw new BadRequestException("Can't move a candidate from " + from + " to " + to + ".");
        }

        applyDecision(candidate, to, null, request.getRemarks(), request.getRejectionReason());

        Candidate saved = candidateRepository.save(candidate);
        auditLogService.log("Candidate", saved.getId(), "STAGE_CHANGE",
                "'" + saved.getFullName() + "': " + from + " -> " + to);
        return CandidateDTO.from(saved);
    }

    /**
     * "Select for Manager Round": HR assigns a hiring manager, schedules
     * the round 2 interview (date/time/Google Meet link), advances the
     * candidate's stage, and emails both the manager and the candidate -
     * all as one atomic action, per the Manager Interview Assignment
     * workflow. Requires the candidate to have just finished the HR round
     * (ROUND1), same as any other ROUND1 -> ROUND2 advance.
     */
    @Transactional
    public CandidateDTO assignManagerRound(Long id, CandidateDTO.AssignManagerRequest request) {
        Candidate candidate = findActiveOrThrow(id);
        if (!"ROUND1".equals(candidate.getStage())) {
            throw new BadRequestException("Only a candidate currently in the HR interview round (ROUND1) can be selected for the manager round - this candidate is " + candidate.getStage() + ".");
        }

        Employee manager = employeeRepository.findById(request.getManagerEmployeeId())
                .orElseThrow(() -> new BadRequestException("Unknown hiring manager: " + request.getManagerEmployeeId()));

        Interview interview = new Interview();
        interview.setCandidate(candidate);
        interview.setInterviewer(manager);
        interview.setRoundNumber(2);
        interview.setRoundType("HIRING_MANAGER");
        interview.setScheduledAt(request.getScheduledAt());
        interview.setMode("VIDEO");
        interview.setStatus("SCHEDULED");
        interview.setMeetingLink(request.getMeetingLink());
        interview.setInstructions(request.getInstructions());
        Interview savedInterview = interviewRepository.save(interview);

        candidate.setStage("ROUND2");
        Candidate savedCandidate = candidateRepository.save(candidate);

        auditLogService.log("Candidate", savedCandidate.getId(), "MANAGER_ROUND_ASSIGNED",
                "'" + savedCandidate.getFullName() + "' assigned to manager round with '" + manager.getFullName()
                        + "' at " + request.getScheduledAt());

        emailService.sendManagerAssignmentEmail(savedCandidate, savedInterview, manager);
        emailService.sendCandidateManagerRoundEmail(savedCandidate, savedInterview, manager);

        return CandidateDTO.from(savedCandidate);
    }

    /**
     * After Round 3 clears, HR generates the offer. This only records the
     * offer terms and moves the candidate to OFFERED - it does NOT email
     * anything. HR must separately upload a signed offer letter
     * (uploadOfferLetter) and explicitly send it (sendOfferLetter) before
     * the candidate is notified.
     */
    @Transactional
    public CandidateDTO generateOffer(Long id, CandidateDTO.OfferRequest request) {
        Candidate candidate = findActiveOrThrow(id);
        if (!"ROUND3".equals(candidate.getStage())) {
            throw new BadRequestException("An offer can only be generated after the candidate has cleared Round 3 (Final/Management Interview).");
        }

        candidate.setOfferAmount(request.getOfferAmount());
        candidate.setExpectedJoiningDate(request.getExpectedJoiningDate());
        candidate.setOfferGeneratedAt(LocalDateTime.now());
        candidate.setStage("OFFERED");

        Candidate saved = candidateRepository.save(candidate);
        auditLogService.log("Candidate", saved.getId(), "OFFER_GENERATED",
                "Offer generated for '" + saved.getFullName() + "': " + request.getOfferAmount()
                        + ", joining " + request.getExpectedJoiningDate());

        return CandidateDTO.from(saved);
    }

    /**
     * HR uploads (or replaces) the offer letter document for a candidate
     * whose offer has been generated. Allowed while OFFERED (first
     * upload) or OFFER_LETTER_SENT (replacing before a resend) - does not
     * itself change the candidate's stage or send anything. Replacing an
     * existing file deletes the old S3 object.
     */
    @Transactional
    public CandidateDTO uploadOfferLetter(Long id, MultipartFile file) {
        Candidate candidate = findActiveOrThrow(id);
        if (!OFFER_LETTER_STAGES.contains(candidate.getStage())) {
            throw new BadRequestException("An offer letter can only be uploaded after an offer has been generated for this candidate.");
        }

        String previousKey = candidate.getOfferLetterFileKey();
        String key = offerLetterStorageService.store(file);

        candidate.setOfferLetterFileKey(key);
        candidate.setOfferLetterOriginalName(file.getOriginalFilename());
        candidate.setOfferLetterUploadedAt(LocalDateTime.now());
        candidate.setOfferLetterUploadedBy(currentActorName());

        Candidate saved = candidateRepository.save(candidate);

        if (previousKey != null && !previousKey.isBlank()) {
            offerLetterStorageService.delete(previousKey);
        }

        auditLogService.log("Candidate", saved.getId(), "OFFER_LETTER_UPLOADED",
                "Offer letter uploaded for '" + saved.getFullName() + "': " + file.getOriginalFilename());

        return CandidateDTO.from(saved);
    }

    /**
     * HR clicks "Send Offer Letter" (or "Resend"): emails the currently-
     * uploaded document as an attachment and moves the candidate to
     * OFFER_LETTER_SENT. The stage change and offerLetterSentAt timestamp
     * are recorded regardless of email outcome - HR took the action, and
     * offerLetterEmailStatus (SENT/FAILED) separately records whether
     * delivery actually succeeded, so a delivery failure can be resent
     * without HR having to redo the upload.
     */
    @Transactional
    public CandidateDTO sendOfferLetter(Long id) {
        Candidate candidate = findActiveOrThrow(id);
        if (!OFFER_LETTER_STAGES.contains(candidate.getStage())) {
            throw new BadRequestException("An offer letter can only be sent after an offer has been generated for this candidate.");
        }
        if (candidate.getOfferLetterFileKey() == null || candidate.getOfferLetterFileKey().isBlank()) {
            throw new BadRequestException("Please upload an offer letter before sending it.");
        }

        byte[] fileBytes = offerLetterStorageService.retrieveBytes(candidate.getOfferLetterFileKey());
        String filename = candidate.getOfferLetterOriginalName() != null ? candidate.getOfferLetterOriginalName() : "offer-letter";

        boolean delivered = emailService.sendOfferLetterEmail(candidate, fileBytes, filename);

        candidate.setStage("OFFER_LETTER_SENT");
        candidate.setOfferLetterSentAt(LocalDateTime.now());
        candidate.setOfferLetterEmailStatus(delivered ? "SENT" : "FAILED");

        Candidate saved = candidateRepository.save(candidate);
        auditLogService.log("Candidate", saved.getId(), "OFFER_LETTER_SENT",
                "Offer letter emailed to '" + saved.getFullName() + "' (" + saved.getOfferLetterEmailStatus() + ")");

        return CandidateDTO.from(saved);
    }

    /** Offer letter storage key isn't exposed on CandidateDTO - the preview/download endpoints need the raw key. */
    public String getOfferLetterKey(Long id) {
        return findActiveOrThrow(id).getOfferLetterFileKey();
    }

    /**
     * Candidate accepts the offer (recorded by HR - this app has no
     * candidate-facing login/portal). Automatically creates the employee
     * profile and starts onboarding, reusing the same EmployeeService the
     * Employees module itself uses - so the resulting record is a normal
     * employee in every other respect (appears in the directory, gets an
     * employee code, etc.), not a special "recruited" record type.
     */
    @Transactional
    public CandidateDTO acceptOffer(Long id) {
        Candidate candidate = findActiveOrThrow(id);
        if (!"OFFER_LETTER_SENT".equals(candidate.getStage())) {
            throw new BadRequestException("This candidate doesn't have a sent offer letter to accept yet.");
        }

        CreateEmployeeRequest employeeRequest = new CreateEmployeeRequest();
        employeeRequest.setFirstName(candidate.getFirstName());
        employeeRequest.setLastName(candidate.getLastName());
        employeeRequest.setEmail(candidate.getEmail());
        employeeRequest.setPhone(candidate.getPhone());
        employeeRequest.setDateOfJoining(candidate.getExpectedJoiningDate() != null
                ? candidate.getExpectedJoiningDate() : LocalDate.now());
        employeeRequest.setEmploymentType(candidate.getJobOpening().getEmploymentType());
        if (candidate.getJobOpening().getDepartment() != null) {
            employeeRequest.setDepartmentId(candidate.getJobOpening().getDepartment().getId());
        }
        if (candidate.getJobOpening().getDesignation() != null) {
            employeeRequest.setDesignationId(candidate.getJobOpening().getDesignation().getId());
        }
        // Reporting manager defaults to whoever ran this candidate's manager-round
        // interview (round 2, HIRING_MANAGER) - the same person who'll actually
        // manage them day to day, so this is the sensible default rather than
        // leaving it unset for HR to fill in manually after the fact.
        findManagerRoundInterviewer(candidate).ifPresent(m -> employeeRequest.setReportingManagerId(m.getId()));

        EmployeeDetailDTO createdEmployee = employeeService.create(employeeRequest);
        createEmployeeLogin(createdEmployee);

        candidate.setOfferAcceptedAt(LocalDateTime.now());
        candidate.setCreatedEmployeeId(createdEmployee.getId());
        candidate.setStage("HIRED");

        Candidate saved = candidateRepository.save(candidate);
        auditLogService.log("Candidate", saved.getId(), "OFFER_ACCEPTED",
                "'" + saved.getFullName() + "' accepted the offer - onboarded as employee " + createdEmployee.getEmployeeCode());
        return CandidateDTO.from(saved);
    }

    private java.util.Optional<Employee> findManagerRoundInterviewer(Candidate candidate) {
        return interviewRepository.findAllByCandidateIdAndDeletedFalseOrderByScheduledAtDesc(candidate.getId()).stream()
                .filter(i -> i.getRoundNumber() == 2 && i.getInterviewer() != null)
                .findFirst()
                .map(Interview::getInterviewer);
    }

    /** Creates or reuses the employee login and sends its invitation. */
    private void createEmployeeLogin(EmployeeDetailDTO employee) {
        employeeInvitationService.sendInvitation(employee.getId());
    }

    private void applyDecision(Candidate candidate, String newStage, Integer rating, String remarks, String rejectionReason) {
        candidate.setStage(newStage);
        if (rating != null) {
            candidate.setRating(rating);
        }
        if (remarks != null) {
            candidate.setRemarks(remarks);
        }
        if ("REJECTED".equals(newStage)) {
            candidate.setRejectionReason(rejectionReason); // optional, per the workflow spec
        }
    }

    /** Display name for "uploaded by": the current user's linked Employee full name, falling back to their username if no Employee is linked (e.g. an admin-only login). */
    private String currentActorName() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }
        return employeeRepository.findByUser_UsernameAndDeletedFalse(authentication.getName())
                .map(Employee::getFullName)
                .orElse(authentication.getName());
    }

    private Candidate findActiveOrThrow(Long id) {
        Candidate candidate = TenantContext.getCurrentTenant() == null
            ? null : candidateRepository.findByIdAndJobOpening_Company_IdAndDeletedFalse(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + id));
        if (candidate == null) {
            throw new ResourceNotFoundException("Candidate not found: " + id);
        }
        if (candidate.isDeleted()) {
            throw new ResourceNotFoundException("Candidate not found: " + id);
        }
        return candidate;
    }
}
