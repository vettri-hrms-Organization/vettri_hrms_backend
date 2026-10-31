package com.haodaone.probation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.letters.dto.LetterDtos;
import com.haodaone.letters.entity.LetterType;
import com.haodaone.letters.repository.LetterTemplateRepository;
import com.haodaone.letters.service.LetterService;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.performance.suite.PerfContext;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Probation and confirmation. New joiners are put on probation (company default length, or set per person by HR).
 * When the end date is near, the reporting manager recommends confirm / extend / not confirm, and HR decides:
 * confirming can issue the company's confirmation letter; extending moves the end date and restarts the review.
 */
@Service
public class ProbationService {

    public static final Set<String> DECISIONS = Set.of("CONFIRM", "EXTEND", "NOT_CONFIRM");

    private final EmployeeProbationRepository probations;
    private final ProbationSettingsRepository settingsRepository;
    private final EmployeeRepository employees;
    private final LetterTemplateRepository letterTemplates;
    private final LetterService letterService;
    private final NotificationService notificationService;
    private final PerfContext ctx;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProbationService(EmployeeProbationRepository probations, ProbationSettingsRepository settingsRepository, EmployeeRepository employees,
                            LetterTemplateRepository letterTemplates, LetterService letterService, NotificationService notificationService,
                            PerfContext ctx, AuditLogService auditLogService, ObjectMapper objectMapper, Clock applicationClock) {
        this.probations = probations;
        this.settingsRepository = settingsRepository;
        this.employees = employees;
        this.letterTemplates = letterTemplates;
        this.letterService = letterService;
        this.notificationService = notificationService;
        this.ctx = ctx;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = applicationClock;
    }

    public record SettingsView(boolean enabled, int defaultMonths, int reviewWindowDays) { }
    public record HistoryEntry(String at, String by, String event, String note) { }
    public record ProbationDTO(Long id, Long employeeId, String employeeName, String employeeCode, String designation, LocalDate dateOfJoining,
                               LocalDate probationEnd, long daysLeft, String status, Long managerEmployeeId, String managerName,
                               String managerRecommendation, String managerFeedback, LocalDateTime managerSubmittedAt, String hrNote,
                               String decidedByName, LocalDateTime decidedAt, int extensions, Long letterId, List<HistoryEntry> history,
                               boolean canRecommend, boolean canDecide) { }
    public record StartRequest(Long employeeId, LocalDate probationEnd) { }
    public record RecommendRequest(String recommendation, String feedback) { }
    public record DecideRequest(String decision, String note, LocalDate newEnd, Boolean issueLetter) { }

    // ------------------------------------------------------------------ settings

    @Transactional(readOnly = true)
    public SettingsView settings() {
        requireHr();
        ProbationSettings s = settingsRepository.findFirstByCompanyIdAndDeletedFalse(ctx.companyId()).orElse(null);
        return s == null ? new SettingsView(false, 6, 15) : new SettingsView(s.isEnabled(), s.getDefaultMonths(), s.getReviewWindowDays());
    }

    @Transactional
    public SettingsView saveSettings(SettingsView v, String actor) {
        requireHr();
        if (v == null || v.defaultMonths() < 1 || v.defaultMonths() > 24) throw new BadRequestException("Probation must be 1-24 months");
        if (v.reviewWindowDays() < 1 || v.reviewWindowDays() > 90) throw new BadRequestException("Review window must be 1-90 days");
        ProbationSettings s = settingsRepository.findFirstByCompanyIdAndDeletedFalse(ctx.companyId()).orElseGet(ProbationSettings::new);
        s.setCompanyId(ctx.companyId());
        s.setEnabled(v.enabled());
        s.setDefaultMonths(v.defaultMonths());
        s.setReviewWindowDays(v.reviewWindowDays());
        s = settingsRepository.save(s);
        auditLogService.log("PROBATION_SETTINGS", s.getId(), "UPDATED", actor + " set probation to " + v.defaultMonths() + " months" + (v.enabled() ? "" : " (off)"));
        if (v.enabled()) enrollNewJoiners(ctx.companyId(), s, LocalDate.now(clock));
        return new SettingsView(s.isEnabled(), s.getDefaultMonths(), s.getReviewWindowDays());
    }

    // ------------------------------------------------------------------ lists

    @Transactional(readOnly = true)
    public List<ProbationDTO> list() {
        Employee me = ctx.meOrNull();
        boolean hr = ctx.hasOrgScope("EMPLOYEE_MANAGE");
        List<EmployeeProbation> rows = probations.findAllByCompanyIdAndDeletedFalseOrderByProbationEndAsc(ctx.companyId());
        Map<Long, Employee> people = people(rows);
        return rows.stream().filter(p -> hr || (me != null && me.getId().equals(managerOf(p, people))))
                .map(p -> toDto(p, people, me, hr)).toList();
    }

    @Transactional(readOnly = true)
    public ProbationDTO mine() {
        Employee me = ctx.me();
        return probations.findFirstByEmployeeIdAndDeletedFalse(me.getId()).map(p -> {
            ProbationDTO full = toDto(p, people(List.of(p)), me, false);
            boolean decided = EmployeeProbation.CONFIRMED.equals(p.getStatus()) || EmployeeProbation.NOT_CONFIRMED.equals(p.getStatus());
            // The employee sees their dates and the outcome, not the manager's recommendation while it's under review.
            return new ProbationDTO(full.id(), full.employeeId(), full.employeeName(), full.employeeCode(), full.designation(), full.dateOfJoining(),
                    full.probationEnd(), full.daysLeft(), full.status(), full.managerEmployeeId(), full.managerName(), null, null, null,
                    decided ? full.hrNote() : null, full.decidedByName(), full.decidedAt(), full.extensions(), full.letterId(), List.of(), false, false);
        }).orElse(null);
    }

    // ------------------------------------------------------------------ actions

    @Transactional
    public ProbationDTO start(StartRequest r, String actor) {
        requireHr();
        if (r == null || r.employeeId() == null || r.probationEnd() == null) throw new BadRequestException("Choose the employee and probation end date");
        Employee e = ctx.employee(r.employeeId());
        EmployeeProbation p = probations.findFirstByEmployeeIdAndDeletedFalse(e.getId()).orElse(null);
        if (p != null && EmployeeProbation.CONFIRMED.equals(p.getStatus())) throw new BadRequestException(e.getFullName() + " is already confirmed");
        if (p == null) p = blank(e);
        p.setProbationEnd(r.probationEnd());
        p.setStatus(EmployeeProbation.ON_PROBATION);
        history(p, actor, "PROBATION_SET", "Probation ends " + r.probationEnd());
        p = probations.save(p);
        openDueReview(p, LocalDate.now(clock), window(p.getCompanyId()));
        return toDto(p, people(List.of(p)), ctx.meOrNull(), true);
    }

    /** Reporting manager's recommendation (HR can also submit it when the employee has no manager). */
    @Transactional
    public ProbationDTO recommend(Long id, RecommendRequest r) {
        Employee me = ctx.me();
        EmployeeProbation p = probation(id);
        Map<Long, Employee> people = people(List.of(p));
        boolean hr = ctx.hasOrgScope("EMPLOYEE_MANAGE");
        if (!me.getId().equals(managerOf(p, people)) && !hr) throw new AccessDeniedException("Only the reporting manager can recommend");
        if (me.getId().equals(p.getEmployeeId())) throw new AccessDeniedException("You can't review your own probation");
        if (!EmployeeProbation.MANAGER_REVIEW.equals(p.getStatus()) && !EmployeeProbation.ON_PROBATION.equals(p.getStatus()))
            throw new BadRequestException("This probation is not waiting for a manager review");
        String rec = r == null || r.recommendation() == null ? "" : r.recommendation().toUpperCase(Locale.ROOT);
        if (!DECISIONS.contains(rec)) throw new BadRequestException("Choose confirm, extend or not confirm");
        String feedback = PerfContext.text(r.feedback(), 5000);
        if (feedback == null) throw new BadRequestException("Add your feedback");
        p.setManagerEmployeeId(me.getId());
        p.setManagerRecommendation(rec);
        p.setManagerFeedback(feedback);
        p.setManagerSubmittedAt(LocalDateTime.now(clock));
        p.setStatus(EmployeeProbation.HR_REVIEW);
        history(p, me.getFullName(), "RECOMMENDED_" + rec, null);
        probations.save(p);
        Employee e = people.get(p.getEmployeeId());
        notificationService.notifyPermissionRecipients(p.getCompanyId(), p.getEmployeeId(), "EMPLOYEE_MANAGE", "PROBATION",
                "Probation review ready", me.getFullName() + " recommends " + label(rec) + " for " + (e == null ? "an employee" : e.getFullName()) + ".",
                "EMPLOYEE_PROBATION", p.getId());
        return toDto(p, people, me, hr);
    }

    @Transactional
    public ProbationDTO decide(Long id, DecideRequest r, String actor) {
        requireHr();
        Employee me = ctx.meOrNull();
        EmployeeProbation p = probation(id);
        if (me != null && me.getId().equals(p.getEmployeeId())) throw new AccessDeniedException("You can't decide your own probation");
        if (EmployeeProbation.CONFIRMED.equals(p.getStatus()) || EmployeeProbation.NOT_CONFIRMED.equals(p.getStatus()))
            throw new BadRequestException("This probation is already closed");
        String decision = r == null || r.decision() == null ? "" : r.decision().toUpperCase(Locale.ROOT);
        if (!DECISIONS.contains(decision)) throw new BadRequestException("Choose confirm, extend or not confirm");
        Map<Long, Employee> people = people(List.of(p));
        Employee e = people.get(p.getEmployeeId());
        p.setHrNote(PerfContext.text(r.note(), 1000));
        switch (decision) {
            case "CONFIRM" -> {
                p.setStatus(EmployeeProbation.CONFIRMED);
                p.setDecidedAt(LocalDateTime.now(clock));
                p.setDecidedByName(actor);
                if (Boolean.TRUE.equals(r.issueLetter())) p.setLetterId(issueLetter(p));
            }
            case "EXTEND" -> {
                if (r.newEnd() == null || !r.newEnd().isAfter(p.getProbationEnd())) throw new BadRequestException("Choose a new end date after " + p.getProbationEnd());
                p.setProbationEnd(r.newEnd());
                p.setExtensions(p.getExtensions() + 1);
                p.setStatus(EmployeeProbation.ON_PROBATION);
                p.setManagerRecommendation(null);
                p.setManagerFeedback(null);
                p.setManagerSubmittedAt(null);
            }
            default -> {
                p.setStatus(EmployeeProbation.NOT_CONFIRMED);
                p.setDecidedAt(LocalDateTime.now(clock));
                p.setDecidedByName(actor);
            }
        }
        history(p, actor, decision, p.getHrNote());
        probations.save(p);
        ctx.notify(e, "PROBATION", switch (decision) {
            case "CONFIRM" -> "Congratulations - you're confirmed";
            case "EXTEND" -> "Your probation has been extended";
            default -> "Probation outcome";
        }, switch (decision) {
            case "CONFIRM" -> "Your employment has been confirmed." + (p.getLetterId() != null ? " Your confirmation letter is in My Documents." : "");
            case "EXTEND" -> "Your probation now ends on " + p.getProbationEnd() + ".";
            default -> "HR has shared the outcome of your probation review. Please speak to HR.";
        }, "EMPLOYEE_PROBATION", p.getId());
        auditLogService.log("EMPLOYEE_PROBATION", p.getId(), decision, actor + ": " + decision + " for employee " + p.getEmployeeId());
        return toDto(p, people, me, true);
    }

    // ------------------------------------------------------------------ scheduler

    /** Daily (ProbationScheduler): enroll new joiners for companies that use probation, and open due reviews. */
    @Transactional
    public int runDaily(LocalDate today) {
        int opened = 0;
        for (ProbationSettings s : settingsRepository.findAllByEnabledTrueAndDeletedFalse()) {
            Long previous = TenantContext.getCurrentTenant();
            TenantContext.setCurrentTenant(s.getCompanyId());
            try {
                enrollNewJoiners(s.getCompanyId(), s, today);
                for (EmployeeProbation p : probations.findAllByCompanyIdAndDeletedFalseOrderByProbationEndAsc(s.getCompanyId())) {
                    if (openDueReview(p, today, s.getReviewWindowDays())) opened++;
                }
            } finally {
                TenantContext.setCurrentTenant(previous);
            }
        }
        return opened;
    }

    private void enrollNewJoiners(Long companyId, ProbationSettings s, LocalDate today) {
        for (Employee e : employees.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId)) {
            if (e.getDateOfJoining() == null) continue;
            LocalDate end = e.getDateOfJoining().plusMonths(s.getDefaultMonths());
            if (!end.isAfter(today.minusDays(1)) || probations.findFirstByEmployeeIdAndDeletedFalse(e.getId()).isPresent()) continue;
            String status = com.haodaone.employee.entity.EmploymentStatus.normalize(e.getStatus());
            if (com.haodaone.employee.entity.EmploymentStatus.RESIGNED.equals(status) || com.haodaone.employee.entity.EmploymentStatus.TERMINATED.equals(status)) continue;
            EmployeeProbation p = blank(e);
            p.setProbationEnd(end);
            p.setStatus(EmployeeProbation.ON_PROBATION);
            history(p, "System", "PROBATION_SET", "Probation ends " + end);
            probations.save(p);
        }
    }

    private boolean openDueReview(EmployeeProbation p, LocalDate today, int windowDays) {
        if (!EmployeeProbation.ON_PROBATION.equals(p.getStatus()) || p.getProbationEnd().minusDays(windowDays).isAfter(today)) return false;
        Employee e = employees.findById(p.getEmployeeId()).orElse(null);
        if (e == null) return false;
        Employee manager = e.getReportingManager();
        p.setManagerEmployeeId(manager == null ? null : manager.getId());
        p.setStatus(manager == null ? EmployeeProbation.HR_REVIEW : EmployeeProbation.MANAGER_REVIEW);
        history(p, "System", "REVIEW_OPENED", null);
        probations.save(p);
        String msg = e.getFullName() + "'s probation ends on " + p.getProbationEnd() + ". Please share your recommendation.";
        if (manager != null && manager.getUser() != null) {
            notificationService.notifyUser(p.getCompanyId(), manager.getUser().getId(), "PROBATION", "Probation review due", msg, "EMPLOYEE_PROBATION", p.getId(), "NORMAL");
        } else {
            notificationService.notifyPermissionRecipients(p.getCompanyId(), e.getId(), "EMPLOYEE_MANAGE", "PROBATION", "Probation review due", msg, "EMPLOYEE_PROBATION", p.getId());
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private Long issueLetter(EmployeeProbation p) {
        var template = letterTemplates.findFirstByCompany_IdAndLetterTypeAndActiveTrueAndDeletedFalseOrderByIdAsc(p.getCompanyId(), LetterType.CONFIRMATION)
                .orElseThrow(() -> new BadRequestException("No active confirmation letter template - add one under Settings > Letter templates, or confirm without a letter."));
        return letterService.generateForEmployee(new LetterDtos.GenerateRequest(template.getId(), p.getEmployeeId(), null, Map.of(), null, false)).id();
    }

    private EmployeeProbation blank(Employee e) {
        EmployeeProbation p = new EmployeeProbation();
        p.setCompanyId(e.getCompany().getId());
        p.setEmployeeId(e.getId());
        p.setExtensions(0);
        return p;
    }

    private int window(Long companyId) {
        return settingsRepository.findFirstByCompanyIdAndDeletedFalse(companyId).map(ProbationSettings::getReviewWindowDays).orElse(15);
    }

    private void history(EmployeeProbation p, String by, String event, String note) {
        List<HistoryEntry> list = new ArrayList<>(history(p));
        list.add(new HistoryEntry(LocalDateTime.now(clock).withNano(0).toString(), by, event, note));
        try {
            p.setHistoryJson(objectMapper.writeValueAsString(list));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not save probation history", ex);
        }
    }

    private List<HistoryEntry> history(EmployeeProbation p) {
        if (p.getHistoryJson() == null || p.getHistoryJson().isBlank()) return List.of();
        try {
            return objectMapper.readValue(p.getHistoryJson(), new TypeReference<List<HistoryEntry>>() { });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<Long, Employee> people(List<EmployeeProbation> rows) {
        List<Long> ids = new ArrayList<>();
        rows.forEach(p -> { ids.add(p.getEmployeeId()); ids.add(p.getManagerEmployeeId()); });
        return ctx.people(ids);
    }

    /** The probation's assigned manager, or the employee's current reporting manager before a review opens. */
    private static Long managerOf(EmployeeProbation p, Map<Long, Employee> people) {
        if (p.getManagerEmployeeId() != null) return p.getManagerEmployeeId();
        Employee e = people.get(p.getEmployeeId());
        return e == null || e.getReportingManager() == null ? null : e.getReportingManager().getId();
    }

    private void requireHr() {
        if (!ctx.hasOrgScope("EMPLOYEE_MANAGE")) throw new AccessDeniedException("Only HR can manage probation");
    }

    private EmployeeProbation probation(Long id) {
        return probations.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Probation record not found"));
    }

    private static String label(String rec) {
        return switch (rec) { case "CONFIRM" -> "confirmation"; case "EXTEND" -> "an extension"; default -> "not confirming"; };
    }

    private ProbationDTO toDto(EmployeeProbation p, Map<Long, Employee> people, Employee me, boolean hr) {
        Employee e = people.get(p.getEmployeeId());
        Long managerId = managerOf(p, people);
        Employee m = managerId == null ? null : people.getOrDefault(managerId, e == null ? null : e.getReportingManager());
        boolean open = !EmployeeProbation.CONFIRMED.equals(p.getStatus()) && !EmployeeProbation.NOT_CONFIRMED.equals(p.getStatus());
        boolean isManager = me != null && me.getId().equals(managerId);
        boolean self = me != null && me.getId().equals(p.getEmployeeId());
        return new ProbationDTO(p.getId(), p.getEmployeeId(), e == null ? null : e.getFullName(), e == null ? null : e.getEmployeeCode(),
                e == null || e.getDesignation() == null ? null : e.getDesignation().getTitle(), e == null ? null : e.getDateOfJoining(),
                p.getProbationEnd(), java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(clock), p.getProbationEnd()), p.getStatus(),
                managerId, m == null ? null : m.getFullName(), p.getManagerRecommendation(), p.getManagerFeedback(), p.getManagerSubmittedAt(),
                p.getHrNote(), p.getDecidedByName(), p.getDecidedAt(), p.getExtensions(), p.getLetterId(), history(p),
                !self && (isManager || hr) && (EmployeeProbation.MANAGER_REVIEW.equals(p.getStatus()) || EmployeeProbation.ON_PROBATION.equals(p.getStatus())),
                !self && hr && open);
    }
}
