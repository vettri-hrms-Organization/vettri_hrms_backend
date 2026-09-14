package com.haodaone.recruitment.controller;

import com.haodaone.audit.entity.AuditLog;
import com.haodaone.audit.repository.AuditLogRepository;
import com.haodaone.recruitment.dto.CandidateDTO;
import com.haodaone.recruitment.entity.Interview;
import com.haodaone.recruitment.repository.InterviewRepository;
import com.haodaone.recruitment.service.CandidateService;
import com.haodaone.recruitment.service.OfferLetterS3StorageService;
import com.haodaone.recruitment.service.ResumeS3StorageService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/candidates")
public class CandidateController {

    private final CandidateService candidateService;
    private final ResumeS3StorageService resumeStorageService;
    private final OfferLetterS3StorageService offerLetterStorageService;
    private final AuditLogRepository auditLogRepository;
    private final InterviewRepository interviewRepository;

    public CandidateController(CandidateService candidateService, ResumeS3StorageService resumeStorageService,
                                OfferLetterS3StorageService offerLetterStorageService, AuditLogRepository auditLogRepository,
                                InterviewRepository interviewRepository) {
        this.candidateService = candidateService;
        this.resumeStorageService = resumeStorageService;
        this.offerLetterStorageService = offerLetterStorageService;
        this.auditLogRepository = auditLogRepository;
        this.interviewRepository = interviewRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RECRUITMENT_VIEW')")
    public List<CandidateDTO> listAll(@RequestParam(required = false) Long jobOpeningId) {
        return candidateService.listAll(jobOpeningId);
    }

    /**
     * Every review/advance/notes/offer action on this candidate already
     * writes an AuditLogService.log("Candidate", id, ...) entry - this
     * just surfaces that existing trail as a timeline instead of adding a
     * second, parallel "candidate events" table that would duplicate it.
     * Scoped to RECRUITMENT_VIEW rather than the general AUDIT_VIEW the
     * org-wide audit log requires, since a recruiter needs their own
     * candidates' history, not admin access to every entity's audit trail.
     *
     * Interview scheduling/decisions log under entityName="Interview" with
     * the interview's own ID, not the candidate's - missing those would
     * make this timeline silently incomplete for exactly the events
     * (interview scheduled, decision made) a recruiter most wants to see.
     * Resolved by finding this candidate's interview IDs first, then
     * pulling both entity types and merging by timestamp.
     */
    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasAuthority('RECRUITMENT_VIEW')")
    public List<AuditLog> timeline(@PathVariable Long id) {
        candidateService.getById(id); // 404s cleanly if the candidate doesn't exist, same as every other /{id} endpoint here

        List<AuditLog> candidateEvents = auditLogRepository.findByEntityNameAndEntityIdOrderByPerformedAtDesc("Candidate", id);

        List<Long> interviewIds = interviewRepository.findAllByCandidateIdAndDeletedFalseOrderByScheduledAtDesc(id).stream()
                .map(Interview::getId)
                .toList();
        List<AuditLog> interviewEvents = interviewIds.stream()
                .flatMap(interviewId -> auditLogRepository.findByEntityNameAndEntityIdOrderByPerformedAtDesc("Interview", interviewId).stream())
                .toList();

        return Stream.concat(candidateEvents.stream(), interviewEvents.stream())
                .sorted(Comparator.comparing(AuditLog::getPerformedAt).reversed())
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RECRUITMENT_VIEW')")
    public CandidateDTO getById(@PathVariable Long id) {
        return candidateService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public ResponseEntity<CandidateDTO> create(@Valid @RequestBody CandidateDTO.CreateRequest request) {
        return ResponseEntity.status(201).body(candidateService.create(request));
    }

    /** HR's initial screening decision on an APPLIED candidate: shortlist, hold, or reject (with optional reason, rating, remarks). */
    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public CandidateDTO review(@PathVariable Long id, @Valid @RequestBody CandidateDTO.ReviewRequest request) {
        return candidateService.review(id, request);
    }

    /** Free-text recruiter notes on this candidate - see CandidateService#updateNotes for why this stays a single overwritable field rather than a note-per-entry history. */
    @PatchMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public CandidateDTO updateNotes(@PathVariable Long id, @Valid @RequestBody CandidateDTO.UpdateNotesRequest request) {
        return candidateService.updateNotes(id, request);
    }

    /** Round-by-round advancement (or hold/reject) once past the initial review. Also how HR records Reject/Hold after the HR interview - "Select for Manager Round" instead goes through /assign-manager below, since it needs scheduling details. */
    @PatchMapping("/{id}/advance")
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public CandidateDTO advance(@PathVariable Long id, @Valid @RequestBody CandidateDTO.AdvanceStageRequest request) {
        return candidateService.advance(id, request);
    }

    /** "Select for Manager Round": assigns the hiring manager + schedule, advances the candidate to ROUND2, and emails both parties. */
    @PostMapping("/{id}/assign-manager")
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public CandidateDTO assignManagerRound(@PathVariable Long id, @Valid @RequestBody CandidateDTO.AssignManagerRequest request) {
        return candidateService.assignManagerRound(id, request);
    }

    /** After Round 3 (final/management interview) clears. */
    @PostMapping("/{id}/generate-offer")
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public CandidateDTO generateOffer(@PathVariable Long id, @Valid @RequestBody CandidateDTO.OfferRequest request) {
        return candidateService.generateOffer(id, request);
    }

    /** Recorded by HR once the candidate confirms acceptance - auto-creates the employee profile and starts onboarding. */
    @PostMapping("/{id}/accept-offer")
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public CandidateDTO acceptOffer(@PathVariable Long id) {
        return candidateService.acceptOffer(id);
    }

    @GetMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('RECRUITMENT_VIEW')")
    public ResponseEntity<InputStreamResource> downloadResume(@PathVariable Long id) {
        CandidateDTO candidate = candidateService.getById(id);
        InputStreamResource resource = resumeStorageService.retrieve(resumeKeyOf(id));
        String filename = candidate.getResumeOriginalName() != null ? candidate.getResumeOriginalName() : "resume";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename.replace("\"", "") + "\"")
                .body(resource);
    }

    /**
     * Same bytes as downloadResume, streamed inline (Content-Disposition:
     * inline) so the frontend can open it in a new tab and let the
     * browser's own PDF viewer render it, rather than forcing a download -
     * same pattern as previewOfferLetter below, which this mirrors.
     */
    @GetMapping("/{id}/resume/preview")
    @PreAuthorize("hasAuthority('RECRUITMENT_VIEW')")
    public ResponseEntity<InputStreamResource> previewResume(@PathVariable Long id) {
        CandidateDTO candidate = candidateService.getById(id);
        InputStreamResource resource = resumeStorageService.retrieve(resumeKeyOf(id));
        String filename = candidate.getResumeOriginalName() != null ? candidate.getResumeOriginalName() : "resume";
        return ResponseEntity.ok()
                .contentType(contentTypeFor(filename))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename.replace("\"", "") + "\"")
                .body(resource);
    }

    /** The resume file key isn't part of CandidateDTO (only hasResume/resumeOriginalName are, to avoid leaking storage internals) - look it up directly for the download. */
    private String resumeKeyOf(Long candidateId) {
        return candidateService.getResumeKey(candidateId);
    }

    /** HR uploads (or replaces) the offer letter document once an offer has been generated. */
    @PostMapping(value = "/{id}/offer-letter", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public CandidateDTO uploadOfferLetter(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return candidateService.uploadOfferLetter(id, file);
    }

    /**
     * Streams the currently-uploaded offer letter inline (for the browser's
     * built-in PDF/document preview) rather than forcing a download - same
     * bytes as downloadOfferLetter, different Content-Disposition.
     */
    @GetMapping("/{id}/offer-letter/preview")
    @PreAuthorize("hasAuthority('RECRUITMENT_VIEW')")
    public ResponseEntity<InputStreamResource> previewOfferLetter(@PathVariable Long id) {
        CandidateDTO candidate = candidateService.getById(id);
        InputStreamResource resource = offerLetterStorageService.retrieve(candidateService.getOfferLetterKey(id));
        String filename = candidate.getOfferLetterOriginalName() != null ? candidate.getOfferLetterOriginalName() : "offer-letter";
        return ResponseEntity.ok()
                .contentType(contentTypeFor(filename))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename.replace("\"", "") + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/offer-letter")
    @PreAuthorize("hasAuthority('RECRUITMENT_VIEW')")
    public ResponseEntity<InputStreamResource> downloadOfferLetter(@PathVariable Long id) {
        CandidateDTO candidate = candidateService.getById(id);
        InputStreamResource resource = offerLetterStorageService.retrieve(candidateService.getOfferLetterKey(id));
        String filename = candidate.getOfferLetterOriginalName() != null ? candidate.getOfferLetterOriginalName() : "offer-letter";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename.replace("\"", "") + "\"")
                .body(resource);
    }

    /** Emails the uploaded offer letter to the candidate and moves the stage to OFFER_LETTER_SENT. Same endpoint covers the initial send and any later resend. */
    @PostMapping("/{id}/send-offer-letter")
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public CandidateDTO sendOfferLetter(@PathVariable Long id) {
        return candidateService.sendOfferLetter(id);
    }

    private MediaType contentTypeFor(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".doc")) return MediaType.valueOf("application/msword");
        if (lower.endsWith(".docx")) return MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
