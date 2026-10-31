package com.haodaone.exit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.employee.service.EmployeeService;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Employee-initiated resignation, Keka-style: the employee resigns, their reporting manager reviews,
 * HR accepts with the final last working day (the employee then serves notice with login until that day,
 * the exit checklist starts and full &amp; final becomes available), and the employee completes an exit
 * interview. Either reviewer can retain the employee instead. Nobody approves their own resignation.
 */
@Service
public class ResignationService {

    public static final List<String> REASONS = List.of("Better opportunity", "Compensation", "Career growth", "Higher studies",
            "Relocation", "Personal or family", "Health", "Work environment", "Manager or team", "Other");
    /** Exit interview: rating questions (1-5), yes/no questions, and free text. */
    public static final List<String> RATING_QUESTIONS = List.of("role", "manager", "growth", "compensation", "culture", "workLife");
    public static final List<String> YES_NO_QUESTIONS = List.of("wouldRecommend", "wouldRejoin");
    public static final List<String> TEXT_QUESTIONS = List.of("likedMost", "improve", "comments");

    private final EmployeeResignationRepository resignations;
    private final ExitSettingsRepository settingsRepository;
    private final EmployeeRepository employees;
    private final EmployeeService employeeService;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ResignationService(EmployeeResignationRepository resignations, ExitSettingsRepository settingsRepository,
                              EmployeeRepository employees, EmployeeService employeeService,
                              AuthorizationService authorizationService, NotificationService notificationService,
                              AuditLogService auditLogService, ObjectMapper objectMapper, Clock applicationClock) {
        this.resignations = resignations;
        this.settingsRepository = settingsRepository;
        this.employees = employees;
        this.employeeService = employeeService;
        this.authorizationService = authorizationService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = applicationClock;
    }

    // ------------------------------------------------------------------ DTOs

    public record SettingsDTO(int noticePeriodDays, boolean managerApproval, boolean exitInterview) { }
    public record ResignationDTO(Long id, Long employeeId, String employeeName, String employeeCode, String departmentName,
                                 String designation, LocalDate dateOfJoining, String status, String reasonCategory, String reason,
                                 LocalDate resignationDate, int noticePeriodDays, LocalDate policyLastWorkingDate,
                                 LocalDate requestedLastWorkingDate, LocalDate approvedLastWorkingDate, int noticeShortfallDays,
                                 Long managerEmployeeId, String managerName, String managerDecisionBy, LocalDateTime managerDecisionAt,
                                 String managerNote, LocalDate managerRecommendedLastWorkingDate, String hrDecisionBy,
                                 LocalDateTime hrDecisionAt, String hrNote, boolean interviewDone, Map<String, Object> interviewAnswers,
                                 boolean canManagerReview, boolean canHrReview, boolean canWithdraw, boolean canCancel,
                                 boolean canTakeInterview) { }
    public record MyView(SettingsDTO settings, boolean canResign, String blockedReason, LocalDate policyLastWorkingDateIfToday,
                         List<String> reasons, ResignationDTO current, List<ResignationDTO> history) { }
    public record SubmitRequest(String reasonCategory, String reason, LocalDate requestedLastWorkingDate) { }
    public record ReviewRequest(String note, LocalDate lastWorkingDate) { }
    public record Summary(int open, int pendingManager, int pendingHr, int servingNotice, int exitedLast12Months,
                          Map<String, Long> reasonsLast12Months, Map<String, Double> averageRatings,
                          double wouldRecommendPercent, int interviewsCompleted) { }

    // ------------------------------------------------------------------ settings

    @Transactional(readOnly = true)
    public SettingsDTO settings() {
        ExitSettings s = settingsFor(tenant());
        return new SettingsDTO(s.getNoticePeriodDays(), s.isManagerApproval(), s.isExitInterview());
    }

    @Transactional
    public SettingsDTO updateSettings(SettingsDTO request) {
        if (request == null || request.noticePeriodDays() < 0 || request.noticePeriodDays() > 365) {
            throw new BadRequestException("Notice period must be between 0 and 365 days");
        }
        Long companyId = tenant();
        ExitSettings s = settingsRepository.findByCompanyIdAndDeletedFalse(companyId).orElseGet(() -> {
            ExitSettings created = new ExitSettings();
            created.setCompanyId(companyId);
            return created;
        });
        s.setNoticePeriodDays(request.noticePeriodDays());
        s.setManagerApproval(request.managerApproval());
        s.setExitInterview(request.exitInterview());
        s = settingsRepository.save(s);
        auditLogService.log("EXIT_SETTINGS", s.getId(), "UPDATED", "Notice period " + s.getNoticePeriodDays() + " days");
        return settings();
    }

    // ------------------------------------------------------------------ employee

    @Transactional(readOnly = true)
    public MyView mine() {
        Employee me = currentEmployee();
        ExitSettings s = settingsFor(me.getCompany().getId());
        List<EmployeeResignation> all = resignations.findAllByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(me.getId());
        EmployeeResignation open = all.stream().filter(r -> EmployeeResignation.OPEN.contains(r.getStatus())).findFirst().orElse(null);
        String blocked = open != null ? "You already have a resignation in progress"
                : isExited(me) ? "Your employment has already ended" : null;
        Map<Long, Employee> people = new HashMap<>();
        people.put(me.getId(), me);
        return new MyView(new SettingsDTO(s.getNoticePeriodDays(), s.isManagerApproval(), s.isExitInterview()),
                blocked == null, blocked, today().plusDays(s.getNoticePeriodDays()), REASONS,
                open == null ? null : toDto(open, me, s),
                all.stream().filter(r -> r != open).map(r -> toDto(r, me, s)).toList());
    }

    @Transactional
    public MyView submit(SubmitRequest r) {
        Employee me = currentEmployee();
        if (r == null || r.reasonCategory() == null || !REASONS.contains(r.reasonCategory())) throw new BadRequestException("Choose a reason");
        if (isExited(me)) throw new BadRequestException("Your employment has already ended");
        if (resignations.findAllByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(me.getId()).stream()
                .anyMatch(x -> EmployeeResignation.OPEN.contains(x.getStatus()))) {
            throw new BadRequestException("You already have a resignation in progress");
        }
        ExitSettings s = settingsFor(me.getCompany().getId());
        LocalDate today = today();
        LocalDate policy = today.plusDays(s.getNoticePeriodDays());
        LocalDate requested = r.requestedLastWorkingDate();
        if (requested != null && (requested.isBefore(today) || requested.isAfter(policy.plusDays(90)))) {
            throw new BadRequestException("Choose a last working day between today and " + policy.plusDays(90));
        }
        Employee manager = me.getReportingManager();
        boolean viaManager = s.isManagerApproval() && manager != null && !manager.isDeleted() && manager.getUser() != null;

        EmployeeResignation x = new EmployeeResignation();
        x.setCompanyId(me.getCompany().getId());
        x.setEmployeeId(me.getId());
        x.setStatus(viaManager ? EmployeeResignation.PENDING_MANAGER : EmployeeResignation.PENDING_HR);
        x.setReasonCategory(r.reasonCategory());
        x.setReason(text(r.reason(), 2000));
        x.setResignationDate(today);
        x.setNoticePeriodDays(s.getNoticePeriodDays());
        x.setPolicyLastWorkingDate(policy);
        x.setRequestedLastWorkingDate(requested);
        x.setManagerEmployeeId(manager == null ? null : manager.getId());
        x = resignations.save(x);
        auditLogService.log("RESIGNATION", x.getId(), "SUBMITTED", me.getFullName() + " resigned (" + r.reasonCategory() + ")");
        String message = me.getFullName() + " has resigned. Notice period ends " + policy
                + (requested != null && requested.isBefore(policy) ? "; they asked to leave on " + requested : "") + ".";
        if (viaManager) {
            notificationService.notifyUser(me.getCompany().getId(), manager.getUser().getId(), "RESIGNATION_SUBMITTED",
                    "Resignation to review", message, "RESIGNATION", x.getId(), "HIGH");
        } else {
            notifyHr(me, "Resignation to review", message, x.getId());
        }
        return mine();
    }

    @Transactional
    public MyView withdraw(Long id) {
        Employee me = currentEmployee();
        EmployeeResignation x = load(id);
        if (!x.getEmployeeId().equals(me.getId())) throw new AccessDeniedException("Not your resignation");
        if (!EmployeeResignation.PENDING_MANAGER.equals(x.getStatus()) && !EmployeeResignation.PENDING_HR.equals(x.getStatus())) {
            throw new BadRequestException("It's already accepted. Ask HR to cancel it.");
        }
        x.setStatus(EmployeeResignation.WITHDRAWN);
        x.setWithdrawnAt(LocalDateTime.now(clock));
        resignations.save(x);
        auditLogService.log("RESIGNATION", x.getId(), "WITHDRAWN", me.getFullName() + " withdrew their resignation");
        notifyHr(me, "Resignation withdrawn", me.getFullName() + " withdrew their resignation.", x.getId());
        return mine();
    }

    @Transactional
    public MyView submitInterview(Long id, Map<String, Object> answers) {
        Employee me = currentEmployee();
        EmployeeResignation x = load(id);
        if (!x.getEmployeeId().equals(me.getId())) throw new AccessDeniedException("Not your resignation");
        if (!EmployeeResignation.ACCEPTED.equals(x.getStatus())) throw new BadRequestException("The exit interview opens once your resignation is accepted");
        if (!settingsFor(me.getCompany().getId()).isExitInterview()) throw new BadRequestException("Exit interviews are turned off");
        x.setInterviewAnswers(writeJson(cleanAnswers(answers)));
        x.setInterviewCompletedAt(LocalDateTime.now(clock));
        resignations.save(x);
        auditLogService.log("RESIGNATION", x.getId(), "EXIT_INTERVIEW", "Exit interview completed");
        return mine();
    }

    // ------------------------------------------------------------------ reviewers

    /** Everything the caller can see: HR sees the company, managers their direct reports' resignations. */
    @Transactional(readOnly = true)
    public List<ResignationDTO> list(String status) {
        Long companyId = tenant();
        boolean hr = isHr();
        Employee me = me();
        ExitSettings s = settingsFor(companyId);
        List<EmployeeResignation> rows = resignations.findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(companyId).stream()
                .filter(r -> hr || (me != null && me.getId().equals(r.getManagerEmployeeId())))
                .filter(r -> status == null || status.isBlank() || status.equalsIgnoreCase(r.getStatus())
                        || ("OPEN".equalsIgnoreCase(status) && EmployeeResignation.OPEN.contains(r.getStatus())))
                .toList();
        Map<Long, Employee> people = people(companyId, rows);
        return rows.stream().filter(r -> people.containsKey(r.getEmployeeId())).map(r -> toDto(r, people.get(r.getEmployeeId()), s)).toList();
    }

    /** Resignations waiting on the caller (manager stage for their reports, HR stage for HR). */
    @Transactional(readOnly = true)
    public List<ResignationDTO> pendingForMe() {
        return list(null).stream().filter(r -> r.canManagerReview() || r.canHrReview()).toList();
    }

    @Transactional(readOnly = true)
    public ResignationDTO get(Long id) {
        EmployeeResignation x = load(id);
        Employee e = employees.findByIdAndCompany_IdAndDeletedFalse(x.getEmployeeId(), x.getCompanyId()).orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        Employee me = me();
        boolean allowed = isHr() || (me != null && (me.getId().equals(x.getEmployeeId()) || me.getId().equals(x.getManagerEmployeeId())));
        if (!allowed) throw new AccessDeniedException("You can't view this resignation");
        return toDto(x, e, settingsFor(x.getCompanyId()));
    }

    /** Approve = move on (manager -> HR, or HR accepts); reject = retain the employee (note required). */
    @Transactional
    public ResignationDTO review(Long id, boolean approve, ReviewRequest r, String actorName) {
        EmployeeResignation x = load(id);
        Employee e = employees.findByIdAndCompany_IdAndDeletedFalse(x.getEmployeeId(), x.getCompanyId()).orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        Employee me = me();
        if (me != null && me.getId().equals(e.getId())) throw new BadRequestException("You can't review your own resignation");
        String note = text(r == null ? null : r.note(), 1000);
        LocalDate lwd = r == null ? null : r.lastWorkingDate();
        boolean managerStage = EmployeeResignation.PENDING_MANAGER.equals(x.getStatus());
        boolean hr = isHr();
        boolean isManager = me != null && me.getId().equals(x.getManagerEmployeeId());

        if (managerStage && isManager && !hr) {
            managerDecision(x, e, approve, note, lwd, actorName);
        } else if ((managerStage || EmployeeResignation.PENDING_HR.equals(x.getStatus())) && hr) {
            if (managerStage && isManager) {
                // HR who is also the manager: record the manager step, then decide as HR.
                x.setManagerDecisionBy(actorName);
                x.setManagerDecisionAt(LocalDateTime.now(clock));
            }
            hrDecision(x, e, approve, note, lwd, actorName);
        } else {
            throw new BadRequestException("This resignation isn't waiting for you");
        }
        return toDto(x, e, settingsFor(x.getCompanyId()));
    }

    /** HR reverses an accepted resignation before the last working day: the employee becomes Active again. */
    @Transactional
    public ResignationDTO cancel(Long id, String note, String actorName) {
        if (!isHr()) throw new AccessDeniedException("Only HR can cancel an accepted resignation");
        EmployeeResignation x = load(id);
        if (!EmployeeResignation.ACCEPTED.equals(x.getStatus())) throw new BadRequestException("Only an accepted resignation can be cancelled");
        if (x.getApprovedLastWorkingDate() != null && x.getApprovedLastWorkingDate().isBefore(today())) {
            throw new BadRequestException("The last working day has passed; re-hire the employee instead");
        }
        Employee e = employees.findByIdAndCompany_IdAndDeletedFalse(x.getEmployeeId(), x.getCompanyId()).orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        employeeService.updateStatus(e.getId(), EmploymentStatus.ACTIVE, "Resignation cancelled", null);
        x.setStatus(EmployeeResignation.CANCELLED);
        x.setHrNote(text(note, 1000));
        x.setHrDecisionBy(actorName);
        x.setHrDecisionAt(LocalDateTime.now(clock));
        resignations.save(x);
        auditLogService.log("RESIGNATION", x.getId(), "CANCELLED", actorName + " cancelled " + e.getFullName() + "'s resignation");
        notifyEmployee(e, "Resignation cancelled", "Your resignation has been cancelled and you remain with the company.", x.getId());
        return toDto(x, e, settingsFor(x.getCompanyId()));
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        if (!isHr()) throw new AccessDeniedException("Only HR can see exit analytics");
        Long companyId = tenant();
        List<EmployeeResignation> all = resignations.findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(companyId);
        LocalDate yearAgo = today().minusMonths(12);
        List<EmployeeResignation> accepted = all.stream()
                .filter(r -> EmployeeResignation.ACCEPTED.equals(r.getStatus()) && !r.getResignationDate().isBefore(yearAgo)).toList();
        Map<String, Long> reasons = accepted.stream().collect(Collectors.groupingBy(EmployeeResignation::getReasonCategory, LinkedHashMap::new, Collectors.counting()));
        Map<String, double[]> ratingSums = new LinkedHashMap<>();
        int interviews = 0;
        int recommendYes = 0;
        int recommendAnswered = 0;
        for (EmployeeResignation r : all) {
            if (r.getInterviewAnswers() == null) continue;
            interviews++;
            Map<String, Object> a = readJson(r.getInterviewAnswers());
            for (String q : RATING_QUESTIONS) {
                if (a.get(q) instanceof Number n) {
                    double[] t = ratingSums.computeIfAbsent(q, k -> new double[2]);
                    t[0] += n.doubleValue();
                    t[1]++;
                }
            }
            if (a.get("wouldRecommend") instanceof Boolean b) {
                recommendAnswered++;
                if (b) recommendYes++;
            }
        }
        Map<String, Double> averages = new LinkedHashMap<>();
        ratingSums.forEach((k, t) -> averages.put(k, Math.round(t[0] / t[1] * 10) / 10.0));
        LocalDate today = today();
        return new Summary(
                (int) all.stream().filter(r -> EmployeeResignation.OPEN.contains(r.getStatus())).count(),
                (int) all.stream().filter(r -> EmployeeResignation.PENDING_MANAGER.equals(r.getStatus())).count(),
                (int) all.stream().filter(r -> EmployeeResignation.PENDING_HR.equals(r.getStatus())).count(),
                (int) all.stream().filter(r -> EmployeeResignation.ACCEPTED.equals(r.getStatus()) && r.getApprovedLastWorkingDate() != null
                        && !r.getApprovedLastWorkingDate().isBefore(today)).count(),
                accepted.size(), reasons, averages,
                recommendAnswered == 0 ? 0 : Math.round(recommendYes * 1000.0 / recommendAnswered) / 10.0, interviews);
    }

    // ------------------------------------------------------------------ steps

    private void managerDecision(EmployeeResignation x, Employee e, boolean approve, String note, LocalDate lwd, String actorName) {
        if (!approve && note == null) throw new BadRequestException("Add a note about why you're retaining them");
        if (lwd != null) validateLwd(x, lwd);
        x.setManagerDecisionBy(actorName);
        x.setManagerDecisionAt(LocalDateTime.now(clock));
        x.setManagerNote(note);
        x.setManagerRecommendedLastWorkingDate(lwd);
        if (approve) {
            x.setStatus(EmployeeResignation.PENDING_HR);
            resignations.save(x);
            auditLogService.log("RESIGNATION", x.getId(), "MANAGER_APPROVED", actorName + " approved " + e.getFullName() + "'s resignation");
            notifyHr(e, "Resignation to accept", actorName + " approved " + e.getFullName() + "'s resignation. HR needs to confirm the last working day.", x.getId());
        } else {
            x.setStatus(EmployeeResignation.RETAINED);
            resignations.save(x);
            auditLogService.log("RESIGNATION", x.getId(), "RETAINED_BY_MANAGER", actorName + " retained " + e.getFullName());
            notifyEmployee(e, "About your resignation", actorName + " would like you to stay: " + note, x.getId());
        }
    }

    private void hrDecision(EmployeeResignation x, Employee e, boolean approve, String note, LocalDate lwd, String actorName) {
        if (!approve && note == null) throw new BadRequestException("Add a note about why the resignation isn't accepted");
        x.setHrDecisionBy(actorName);
        x.setHrDecisionAt(LocalDateTime.now(clock));
        x.setHrNote(note);
        if (!approve) {
            x.setStatus(EmployeeResignation.RETAINED);
            resignations.save(x);
            auditLogService.log("RESIGNATION", x.getId(), "RETAINED_BY_HR", actorName + " retained " + e.getFullName());
            notifyEmployee(e, "About your resignation", "HR has not accepted your resignation: " + note, x.getId());
            return;
        }
        LocalDate finalDay = lwd != null ? lwd
                : x.getManagerRecommendedLastWorkingDate() != null ? x.getManagerRecommendedLastWorkingDate()
                : x.getRequestedLastWorkingDate() != null ? x.getRequestedLastWorkingDate()
                : x.getPolicyLastWorkingDate();
        validateLwd(x, finalDay);
        x.setApprovedLastWorkingDate(finalDay);
        x.setStatus(EmployeeResignation.ACCEPTED);
        resignations.save(x);
        // Existing exit path: status Resigned, login kept until the last working day, exit checklist, F&F eligibility.
        employeeService.updateStatus(e.getId(), EmploymentStatus.RESIGNED, "Resignation accepted (" + x.getReasonCategory() + ")", finalDay);
        auditLogService.log("RESIGNATION", x.getId(), "ACCEPTED", actorName + " accepted " + e.getFullName() + "'s resignation; last working day " + finalDay);
        boolean interview = settingsFor(x.getCompanyId()).isExitInterview();
        notifyEmployee(e, "Resignation accepted", "Your last working day is " + finalDay + "."
                + (interview ? " Please take a few minutes to complete your exit interview." : ""), x.getId());
    }

    private void validateLwd(EmployeeResignation x, LocalDate lwd) {
        if (lwd.isBefore(x.getResignationDate())) throw new BadRequestException("The last working day can't be before the resignation date");
        if (lwd.isAfter(x.getPolicyLastWorkingDate().plusDays(180))) throw new BadRequestException("That last working day is too far out");
    }

    // ------------------------------------------------------------------ helpers

    private ResignationDTO toDto(EmployeeResignation x, Employee e, ExitSettings s) {
        Employee me = me();
        boolean self = me != null && me.getId().equals(e.getId());
        boolean hr = isHr();
        boolean isManager = me != null && me.getId().equals(x.getManagerEmployeeId());
        LocalDate lwd = x.getApprovedLastWorkingDate() != null ? x.getApprovedLastWorkingDate()
                : x.getRequestedLastWorkingDate() != null ? x.getRequestedLastWorkingDate() : x.getPolicyLastWorkingDate();
        int shortfall = (int) Math.max(0, ChronoUnit.DAYS.between(lwd, x.getPolicyLastWorkingDate()));
        String managerName = x.getManagerEmployeeId() == null ? null
                : employees.findById(x.getManagerEmployeeId()).map(Employee::getFullName).orElse(null);
        boolean showInterview = self || hr;
        return new ResignationDTO(x.getId(), e.getId(), e.getFullName(), e.getEmployeeCode(),
                e.getDepartment() == null ? null : e.getDepartment().getName(),
                e.getDesignation() == null ? null : e.getDesignation().getTitle(), e.getDateOfJoining(),
                x.getStatus(), x.getReasonCategory(), x.getReason(), x.getResignationDate(), x.getNoticePeriodDays(),
                x.getPolicyLastWorkingDate(), x.getRequestedLastWorkingDate(), x.getApprovedLastWorkingDate(), shortfall,
                x.getManagerEmployeeId(), managerName, x.getManagerDecisionBy(), x.getManagerDecisionAt(), x.getManagerNote(),
                x.getManagerRecommendedLastWorkingDate(), x.getHrDecisionBy(), x.getHrDecisionAt(), x.getHrNote(),
                x.getInterviewCompletedAt() != null,
                showInterview && x.getInterviewAnswers() != null ? readJson(x.getInterviewAnswers()) : null,
                !self && isManager && EmployeeResignation.PENDING_MANAGER.equals(x.getStatus()),
                !self && hr && (EmployeeResignation.PENDING_HR.equals(x.getStatus()) || EmployeeResignation.PENDING_MANAGER.equals(x.getStatus())),
                self && (EmployeeResignation.PENDING_MANAGER.equals(x.getStatus()) || EmployeeResignation.PENDING_HR.equals(x.getStatus())),
                !self && hr && EmployeeResignation.ACCEPTED.equals(x.getStatus()) && x.getApprovedLastWorkingDate() != null
                        && !x.getApprovedLastWorkingDate().isBefore(today()),
                self && EmployeeResignation.ACCEPTED.equals(x.getStatus()) && s.isExitInterview() && x.getInterviewCompletedAt() == null);
    }

    private Map<String, Object> cleanAnswers(Map<String, Object> answers) {
        if (answers == null) throw new BadRequestException("Answer the questions");
        Map<String, Object> clean = new LinkedHashMap<>();
        for (String q : RATING_QUESTIONS) {
            Object v = answers.get(q);
            if (v == null) continue;
            int n;
            try {
                n = Integer.parseInt(String.valueOf(v));
            } catch (NumberFormatException ex) {
                throw new BadRequestException("Ratings must be 1 to 5");
            }
            if (n < 1 || n > 5) throw new BadRequestException("Ratings must be 1 to 5");
            clean.put(q, n);
        }
        for (String q : YES_NO_QUESTIONS) {
            Object v = answers.get(q);
            if (v != null) clean.put(q, Boolean.parseBoolean(String.valueOf(v)));
        }
        for (String q : TEXT_QUESTIONS) {
            String v = text(answers.get(q) == null ? null : String.valueOf(answers.get(q)), 2000);
            if (v != null) clean.put(q, v);
        }
        if (clean.isEmpty()) throw new BadRequestException("Answer at least one question");
        return clean;
    }

    private void notifyHr(Employee e, String title, String message, Long id) {
        notificationService.notifyPermissionRecipients(e.getCompany().getId(), e.getId(), "EMPLOYEE_MANAGE",
                "RESIGNATION", title, message, "RESIGNATION", id);
    }

    private void notifyEmployee(Employee e, String title, String message, Long id) {
        if (e.getUser() == null) return;
        notificationService.notifyUser(e.getCompany().getId(), e.getUser().getId(), "RESIGNATION", title, message, "RESIGNATION", id, "HIGH");
    }

    private Map<Long, Employee> people(Long companyId, List<EmployeeResignation> rows) {
        Set<Long> ids = rows.stream().map(EmployeeResignation::getEmployeeId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return employees.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(companyId, ids).stream()
                .collect(Collectors.toMap(Employee::getId, x -> x));
    }

    private ExitSettings settingsFor(Long companyId) {
        return settingsRepository.findByCompanyIdAndDeletedFalse(companyId).orElseGet(() -> {
            ExitSettings defaults = new ExitSettings();
            defaults.setCompanyId(companyId);
            return defaults;
        });
    }

    private EmployeeResignation load(Long id) {
        return resignations.findByIdAndCompanyIdAndDeletedFalse(id, tenant()).orElseThrow(() -> new ResourceNotFoundException("Resignation not found"));
    }

    private boolean isHr() {
        return authorizationService.hasOrganizationScope("EMPLOYEE_MANAGE");
    }

    private static boolean isExited(Employee e) {
        String s = EmploymentStatus.normalize(e.getStatus());
        return EmploymentStatus.RESIGNED.equals(s) || EmploymentStatus.TERMINATED.equals(s);
    }

    /**
     * The caller's employee record, including people already marked Resigned who are serving notice
     * (AuthorizationService.resolveCurrentEmployee skips those).
     */
    private Employee me() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof com.haodaone.security.CustomUserPrincipal principal)) return null;
        Long companyId = TenantContext.getCurrentTenant();
        return employees.findByUser_IdAndDeletedFalse(principal.getId())
                .filter(e -> e.getCompany() != null && e.getCompany().getId().equals(companyId))
                .orElse(null);
    }

    private Employee currentEmployee() {
        Employee me = me();
        if (me == null || me.getCompany() == null) throw new BadRequestException("Your login isn't linked to an employee record");
        return me;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Could not store the answers", ex);
        }
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return Map.of();
        }
    }

    private static String text(String value, int max) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.length() > max) throw new BadRequestException("Keep it under " + max + " characters");
        return v;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static Long tenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        return companyId;
    }
}
