package com.haodaone.performance.suite;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Review cycles, Keka-style: HR launches a cycle for a set of employees; each writes a self review, peers named by the
 * manager (or HR) give feedback, the reporting manager writes the manager review and rating, HR calibrates the final
 * rating, and results are released to employees, who acknowledge them. Peer feedback is shown to the employee without
 * the reviewer's name. Ratings are on a 1-5 scale.
 */
@Service
public class ReviewCycleService {

    private final PerformanceCycleRepository cycles;
    private final CycleParticipantRepository participants;
    private final ReviewResponseRepository responses;
    private final PerfContext ctx;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public ReviewCycleService(PerformanceCycleRepository cycles, CycleParticipantRepository participants, ReviewResponseRepository responses,
                              PerfContext ctx, AuditLogService auditLogService, Clock applicationClock) {
        this.cycles = cycles;
        this.participants = participants;
        this.responses = responses;
        this.ctx = ctx;
        this.auditLogService = auditLogService;
        this.clock = applicationClock;
    }

    public record CycleRequest(String name, LocalDate periodStart, LocalDate periodEnd, LocalDate selfDue, LocalDate managerDue,
                               Boolean peerReviews, String scope, Long departmentId, List<Long> employeeIds) { }
    public record ResponseDTO(Long id, String kind, String status, String reviewerName, BigDecimal rating, String strengths,
                              String improvements, String comments, LocalDateTime submittedAt) { }
    public record ParticipantDTO(Long id, Long cycleId, String cycleName, String cycleStatus, Long employeeId, String employeeName,
                                 String employeeCode, String designation, Long managerEmployeeId, String managerName, String status,
                                 BigDecimal selfRating, BigDecimal managerRating, BigDecimal peerAverage, BigDecimal finalRating,
                                 String calibrationNote, LocalDateTime releasedAt, LocalDateTime acknowledgedAt, List<ResponseDTO> responses) { }
    public record CycleDTO(Long id, String name, LocalDate periodStart, LocalDate periodEnd, String status, LocalDate selfDue,
                           LocalDate managerDue, boolean peerReviews, int participants, int selfDone, int managerDone, int released,
                           Map<String, Long> ratingDistribution) { }
    public record TaskDTO(Long responseId, String kind, Long participantId, Long cycleId, String cycleName, Long employeeId,
                          String employeeName, LocalDate due) { }
    public record ReviewBody(BigDecimal rating, String strengths, String improvements, String comments) { }

    // ------------------------------------------------------------------ HR

    @Transactional(readOnly = true)
    public List<CycleDTO> cycles() {
        Employee me = ctx.meOrNull();
        boolean hr = ctx.isHr();
        return cycles.findAllByCompanyIdAndDeletedFalseOrderByPeriodEndDesc(ctx.companyId()).stream()
                .filter(c -> hr || (me != null && participants.findAllByCycleIdAndDeletedFalseOrderByIdAsc(c.getId()).stream()
                        .anyMatch(p -> me.getId().equals(p.getManagerEmployeeId()))))
                .map(this::toDto).toList();
    }

    @Transactional
    public CycleDTO create(CycleRequest r, String actor) {
        ctx.requireHr();
        if (r == null || PerfContext.text(r.name(), 120) == null) throw new BadRequestException("Name the cycle");
        if (r.periodStart() == null || r.periodEnd() == null || r.periodEnd().isBefore(r.periodStart())) throw new BadRequestException("Choose a valid review period");
        PerformanceCycle c = new PerformanceCycle();
        c.setCompanyId(ctx.companyId());
        c.setName(r.name().trim());
        c.setPeriodStart(r.periodStart());
        c.setPeriodEnd(r.periodEnd());
        c.setSelfDue(r.selfDue());
        c.setManagerDue(r.managerDue());
        c.setPeerReviews(Boolean.TRUE.equals(r.peerReviews()));
        c.setStatus(PerformanceCycle.DRAFT);
        c.setCreatedByName(actor);
        c = cycles.save(c);
        String scope = r.scope() == null ? "ALL" : r.scope().toUpperCase(Locale.ROOT);
        Set<Long> chosen = r.employeeIds() == null ? Set.of() : new HashSet<>(r.employeeIds());
        int added = 0;
        for (Employee e : ctx.activeEmployees()) {
            if ("DEPARTMENT".equals(scope) && (e.getDepartment() == null || !e.getDepartment().getId().equals(r.departmentId()))) continue;
            if ("SELECTED".equals(scope) && !chosen.contains(e.getId())) continue;
            CycleParticipant p = new CycleParticipant();
            p.setCompanyId(c.getCompanyId());
            p.setCycleId(c.getId());
            p.setEmployeeId(e.getId());
            p.setManagerEmployeeId(e.getReportingManager() == null ? null : e.getReportingManager().getId());
            p.setStatus(CycleParticipant.SELF_PENDING);
            p = participants.save(p);
            response(p, e.getId(), ReviewResponse.SELF);
            if (p.getManagerEmployeeId() != null) response(p, p.getManagerEmployeeId(), ReviewResponse.MANAGER);
            added++;
        }
        if (added == 0) throw new BadRequestException("Nobody matches - choose who's in this cycle");
        auditLogService.log("PERFORMANCE_CYCLE", c.getId(), "CREATED", actor + " created '" + c.getName() + "' for " + added + " employee(s)");
        return toDto(c);
    }

    /** DRAFT -> SELF_REVIEW -> MANAGER_REVIEW -> CALIBRATION; RELEASED goes through {@link #release}. */
    @Transactional
    public CycleDTO advance(Long id, String actor) {
        ctx.requireHr();
        PerformanceCycle c = cycle(id);
        String next = switch (c.getStatus()) {
            case PerformanceCycle.DRAFT -> PerformanceCycle.SELF_REVIEW;
            case PerformanceCycle.SELF_REVIEW -> PerformanceCycle.MANAGER_REVIEW;
            case PerformanceCycle.MANAGER_REVIEW -> PerformanceCycle.CALIBRATION;
            default -> throw new BadRequestException("Use Release to share results");
        };
        c.setStatus(next);
        cycles.save(c);
        Map<Long, Employee> people = ctx.people(participants.findAllByCycleIdAndDeletedFalseOrderByIdAsc(id).stream()
                .flatMap(p -> java.util.stream.Stream.of(p.getEmployeeId(), p.getManagerEmployeeId())).toList());
        if (PerformanceCycle.SELF_REVIEW.equals(next)) {
            for (CycleParticipant p : participants.findAllByCycleIdAndDeletedFalseOrderByIdAsc(id)) {
                ctx.notify(people.get(p.getEmployeeId()), "PERFORMANCE", c.getName() + " has started",
                        "Write your self review" + (c.getSelfDue() == null ? "." : " by " + c.getSelfDue() + "."), "PERFORMANCE_CYCLE", c.getId());
            }
        } else if (PerformanceCycle.MANAGER_REVIEW.equals(next)) {
            Set<Long> managers = new HashSet<>();
            participants.findAllByCycleIdAndDeletedFalseOrderByIdAsc(id).forEach(p -> { if (p.getManagerEmployeeId() != null) managers.add(p.getManagerEmployeeId()); });
            managers.forEach(m -> ctx.notify(people.get(m), "PERFORMANCE", "Manager reviews are open",
                    "Review your team for " + c.getName() + (c.getManagerDue() == null ? "." : " by " + c.getManagerDue() + "."), "PERFORMANCE_CYCLE", c.getId()));
        }
        auditLogService.log("PERFORMANCE_CYCLE", c.getId(), "STAGE", actor + " moved '" + c.getName() + "' to " + next);
        return toDto(c);
    }

    @Transactional(readOnly = true)
    public List<ParticipantDTO> participantsOf(Long cycleId) {
        PerformanceCycle c = cycle(cycleId);
        Employee me = ctx.meOrNull();
        boolean hr = ctx.isHr();
        List<CycleParticipant> rows = participants.findAllByCycleIdAndDeletedFalseOrderByIdAsc(cycleId).stream()
                .filter(p -> hr || (me != null && me.getId().equals(p.getManagerEmployeeId()))).toList();
        if (!hr && rows.isEmpty()) throw new AccessDeniedException("You aren't reviewing anyone in this cycle");
        return rows.stream().map(p -> toDto(p, c, true)).toList();
    }

    @Transactional
    public ParticipantDTO calibrate(Long participantId, BigDecimal finalRating, String note, String actor) {
        ctx.requireHr();
        CycleParticipant p = participant(participantId);
        p.setFinalRating(rating(finalRating));
        p.setCalibrationNote(PerfContext.text(note, 1000));
        participants.save(p);
        auditLogService.log("PERFORMANCE_CYCLE", p.getCycleId(), "CALIBRATED", actor + " set the final rating for participant " + p.getId());
        return toDto(p, cycle(p.getCycleId()), true);
    }

    @Transactional
    public CycleDTO release(Long cycleId, String actor) {
        ctx.requireHr();
        PerformanceCycle c = cycle(cycleId);
        if (PerformanceCycle.RELEASED.equals(c.getStatus())) throw new BadRequestException("Results are already released");
        List<CycleParticipant> rows = participants.findAllByCycleIdAndDeletedFalseOrderByIdAsc(cycleId);
        Map<Long, Employee> people = ctx.people(rows.stream().map(CycleParticipant::getEmployeeId).toList());
        for (CycleParticipant p : rows) {
            if (p.getFinalRating() == null) p.setFinalRating(p.getManagerRating() != null ? p.getManagerRating() : selfRating(p));
            p.setStatus(CycleParticipant.RELEASED);
            p.setReleasedAt(LocalDateTime.now(clock));
            participants.save(p);
            ctx.notify(people.get(p.getEmployeeId()), "PERFORMANCE", "Your review is ready", "Your " + c.getName() + " results are available.", "PERFORMANCE_CYCLE", c.getId());
        }
        c.setStatus(PerformanceCycle.RELEASED);
        cycles.save(c);
        auditLogService.log("PERFORMANCE_CYCLE", c.getId(), "RELEASED", actor + " released '" + c.getName() + "'");
        return toDto(c);
    }

    /** Manager or HR names peers who'll give feedback. */
    @Transactional
    public ParticipantDTO addPeers(Long participantId, List<Long> peerIds) {
        CycleParticipant p = participant(participantId);
        PerformanceCycle c = cycle(p.getCycleId());
        if (!c.isPeerReviews()) throw new BadRequestException("This cycle doesn't use peer reviews");
        Employee me = ctx.me();
        if (!ctx.isHr() && !me.getId().equals(p.getManagerEmployeeId())) throw new AccessDeniedException("Only the manager or HR can choose peers");
        Set<Long> existing = new HashSet<>();
        responses.findAllByParticipantIdAndDeletedFalseOrderByIdAsc(p.getId()).forEach(r -> existing.add(r.getReviewerEmployeeId()));
        Map<Long, Employee> peers = ctx.people(peerIds == null ? List.of() : peerIds);
        Employee subject = ctx.employee(p.getEmployeeId());
        for (Employee peer : peers.values()) {
            if (existing.contains(peer.getId()) || peer.getId().equals(p.getEmployeeId())) continue;
            ReviewResponse r = response(p, peer.getId(), ReviewResponse.PEER);
            ctx.notify(peer, "PERFORMANCE", "Peer feedback requested", "Share feedback on " + subject.getFullName() + " for " + c.getName() + ".", "REVIEW_RESPONSE", r.getId());
        }
        return toDto(p, c, true);
    }

    // ------------------------------------------------------------------ everyone

    @Transactional(readOnly = true)
    public List<TaskDTO> myTasks() {
        Employee me = ctx.me();
        List<TaskDTO> out = new ArrayList<>();
        for (ReviewResponse r : responses.findAllByCompanyIdAndReviewerEmployeeIdAndDeletedFalseOrderByIdDesc(ctx.companyId(), me.getId())) {
            if (!ReviewResponse.PENDING.equals(r.getStatus())) continue;
            CycleParticipant p = participants.findById(r.getParticipantId()).orElse(null);
            if (p == null) continue;
            PerformanceCycle c = cycles.findById(p.getCycleId()).orElse(null);
            if (c == null || PerformanceCycle.DRAFT.equals(c.getStatus()) || PerformanceCycle.RELEASED.equals(c.getStatus())) continue;
            if (ReviewResponse.MANAGER.equals(r.getKind()) && PerformanceCycle.SELF_REVIEW.equals(c.getStatus())) continue;
            Employee subject = ctx.people(List.of(p.getEmployeeId())).get(p.getEmployeeId());
            out.add(new TaskDTO(r.getId(), r.getKind(), p.getId(), c.getId(), c.getName(), p.getEmployeeId(),
                    subject == null ? null : subject.getFullName(), ReviewResponse.MANAGER.equals(r.getKind()) ? c.getManagerDue() : c.getSelfDue()));
        }
        return out;
    }

    @Transactional
    public TaskDTO submit(Long responseId, ReviewBody body) {
        Employee me = ctx.me();
        ReviewResponse r = responses.findByIdAndCompanyIdAndDeletedFalse(responseId, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (!r.getReviewerEmployeeId().equals(me.getId())) throw new AccessDeniedException("This review isn't yours to write");
        if (!ReviewResponse.PENDING.equals(r.getStatus())) throw new BadRequestException("Already submitted");
        CycleParticipant p = participant(r.getParticipantId());
        PerformanceCycle c = cycle(p.getCycleId());
        if (PerformanceCycle.DRAFT.equals(c.getStatus()) || PerformanceCycle.RELEASED.equals(c.getStatus())) throw new BadRequestException("This cycle isn't open");
        if (body == null) throw new BadRequestException("Write your review");
        r.setRating(rating(body.rating()));
        r.setStrengths(PerfContext.text(body.strengths(), 5000));
        r.setImprovements(PerfContext.text(body.improvements(), 5000));
        r.setComments(PerfContext.text(body.comments(), 5000));
        if (r.getStrengths() == null && r.getImprovements() == null && r.getComments() == null) throw new BadRequestException("Add at least one comment");
        r.setStatus(ReviewResponse.SUBMITTED);
        r.setSubmittedAt(LocalDateTime.now(clock));
        responses.save(r);
        if (ReviewResponse.SELF.equals(r.getKind())) {
            p.setStatus(p.getManagerEmployeeId() == null ? CycleParticipant.CALIBRATION : CycleParticipant.MANAGER_PENDING);
            participants.save(p);
        } else if (ReviewResponse.MANAGER.equals(r.getKind())) {
            p.setManagerRating(r.getRating());
            p.setStatus(CycleParticipant.CALIBRATION);
            participants.save(p);
        }
        return new TaskDTO(r.getId(), r.getKind(), p.getId(), c.getId(), c.getName(), p.getEmployeeId(), null, null);
    }

    @Transactional(readOnly = true)
    public List<ParticipantDTO> myResults() {
        Employee me = ctx.me();
        return participants.findAllByEmployeeIdAndDeletedFalseOrderByIdDesc(me.getId()).stream()
                .map(p -> toDto(p, cycle(p.getCycleId()), CycleParticipant.RELEASED.equals(p.getStatus()))).toList();
    }

    @Transactional
    public ParticipantDTO acknowledge(Long participantId) {
        Employee me = ctx.me();
        CycleParticipant p = participant(participantId);
        if (!p.getEmployeeId().equals(me.getId())) throw new AccessDeniedException("Not your review");
        if (!CycleParticipant.RELEASED.equals(p.getStatus())) throw new BadRequestException("Results aren't released yet");
        p.setAcknowledgedAt(LocalDateTime.now(clock));
        participants.save(p);
        return toDto(p, cycle(p.getCycleId()), true);
    }

    // ------------------------------------------------------------------ helpers

    private ReviewResponse response(CycleParticipant p, Long reviewer, String kind) {
        ReviewResponse r = new ReviewResponse();
        r.setCompanyId(p.getCompanyId());
        r.setParticipantId(p.getId());
        r.setReviewerEmployeeId(reviewer);
        r.setKind(kind);
        r.setStatus(ReviewResponse.PENDING);
        return responses.save(r);
    }

    private BigDecimal selfRating(CycleParticipant p) {
        return responses.findAllByParticipantIdAndDeletedFalseOrderByIdAsc(p.getId()).stream()
                .filter(r -> ReviewResponse.SELF.equals(r.getKind()) && r.getRating() != null).map(ReviewResponse::getRating).findFirst().orElse(null);
    }

    private static BigDecimal rating(BigDecimal value) {
        if (value == null) return null;
        if (value.compareTo(BigDecimal.ONE) < 0 || value.compareTo(BigDecimal.valueOf(5)) > 0) throw new BadRequestException("Ratings are from 1 to 5");
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private PerformanceCycle cycle(Long id) {
        return cycles.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Cycle not found"));
    }

    private CycleParticipant participant(Long id) {
        return participants.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Review not found"));
    }

    private CycleDTO toDto(PerformanceCycle c) {
        List<CycleParticipant> rows = participants.findAllByCycleIdAndDeletedFalseOrderByIdAsc(c.getId());
        int selfDone = (int) rows.stream().filter(p -> !CycleParticipant.SELF_PENDING.equals(p.getStatus())).count();
        int managerDone = (int) rows.stream().filter(p -> p.getManagerRating() != null).count();
        int released = (int) rows.stream().filter(p -> CycleParticipant.RELEASED.equals(p.getStatus())).count();
        Map<String, Long> distribution = new TreeMap<>();
        rows.stream().map(p -> p.getFinalRating() != null ? p.getFinalRating() : p.getManagerRating()).filter(Objects::nonNull)
                .forEach(r -> distribution.merge(String.valueOf(r.setScale(0, RoundingMode.HALF_UP).intValue()), 1L, Long::sum));
        return new CycleDTO(c.getId(), c.getName(), c.getPeriodStart(), c.getPeriodEnd(), c.getStatus(), c.getSelfDue(), c.getManagerDue(),
                c.isPeerReviews(), rows.size(), selfDone, managerDone, released, distribution);
    }

    private ParticipantDTO toDto(CycleParticipant p, PerformanceCycle c, boolean withResponses) {
        List<ReviewResponse> all = responses.findAllByParticipantIdAndDeletedFalseOrderByIdAsc(p.getId());
        Employee me = ctx.meOrNull();
        boolean isSubject = me != null && me.getId().equals(p.getEmployeeId());
        Map<Long, Employee> people = ctx.people(new ArrayList<>(List.of(p.getEmployeeId())) {{
            if (p.getManagerEmployeeId() != null) add(p.getManagerEmployeeId());
            all.forEach(r -> add(r.getReviewerEmployeeId()));
        }});
        Employee e = people.get(p.getEmployeeId());
        BigDecimal self = all.stream().filter(r -> ReviewResponse.SELF.equals(r.getKind()) && r.getRating() != null).map(ReviewResponse::getRating).findFirst().orElse(null);
        List<BigDecimal> peerRatings = all.stream().filter(r -> ReviewResponse.PEER.equals(r.getKind()) && r.getRating() != null).map(ReviewResponse::getRating).toList();
        BigDecimal peerAvg = peerRatings.isEmpty() ? null : peerRatings.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(peerRatings.size()), 1, RoundingMode.HALF_UP);
        List<ResponseDTO> visible = !withResponses ? List.of() : all.stream()
                .filter(r -> !isSubject || ReviewResponse.SELF.equals(r.getKind()) || ReviewResponse.SUBMITTED.equals(r.getStatus()))
                .map(r -> new ResponseDTO(r.getId(), r.getKind(), r.getStatus(),
                        isSubject && ReviewResponse.PEER.equals(r.getKind()) ? "A colleague" : PerfContext.name(people, r.getReviewerEmployeeId()),
                        r.getRating(), r.getStrengths(), r.getImprovements(), r.getComments(), r.getSubmittedAt())).toList();
        boolean showRatings = !isSubject || CycleParticipant.RELEASED.equals(p.getStatus());
        return new ParticipantDTO(p.getId(), c.getId(), c.getName(), c.getStatus(), p.getEmployeeId(), e == null ? null : e.getFullName(),
                e == null ? null : e.getEmployeeCode(), e == null || e.getDesignation() == null ? null : e.getDesignation().getTitle(),
                p.getManagerEmployeeId(), PerfContext.name(people, p.getManagerEmployeeId()), p.getStatus(), self,
                showRatings ? p.getManagerRating() : null, showRatings ? peerAvg : null, showRatings ? p.getFinalRating() : null,
                showRatings ? p.getCalibrationNote() : null, p.getReleasedAt(), p.getAcknowledgedAt(), visible);
    }
}
