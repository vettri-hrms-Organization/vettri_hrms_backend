package com.haodaone.performance.suite;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Performance improvement plans. The reporting manager or HR opens a PIP with clear expectations and an end date,
 * records check-ins along the way and closes it with an outcome. The employee sees their own plan and acknowledges it.
 * Other employees never see PIPs.
 */
@Service
public class PipService {

    public static final Set<String> OUTCOMES = Set.of("SUCCESSFUL", "EXTENDED", "UNSUCCESSFUL", "CANCELLED");

    private final ImprovementPlanRepository plans;
    private final PerfContext ctx;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PipService(ImprovementPlanRepository plans, PerfContext ctx, AuditLogService auditLogService, ObjectMapper objectMapper, Clock applicationClock) {
        this.plans = plans;
        this.ctx = ctx;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = applicationClock;
    }

    public record Expectation(String goal, String measure) { }
    public record CheckInNote(LocalDate date, String byName, String note, String rating) { }
    public record PipRequest(Long employeeId, LocalDate startDate, LocalDate endDate, String reason, List<Expectation> expectations) { }
    public record CheckInRequest(String note, String rating) { }
    public record CloseRequest(String outcome, String note, LocalDate newEndDate) { }
    public record PipDTO(Long id, Long employeeId, String employeeName, String employeeCode, Long ownerEmployeeId, String ownerName,
                         LocalDate startDate, LocalDate endDate, String reason, List<Expectation> expectations, List<CheckInNote> checkIns,
                         String status, String outcomeNote, LocalDateTime acknowledgedAt, boolean canManage) { }

    @Transactional(readOnly = true)
    public List<PipDTO> list() {
        Employee me = ctx.meOrNull();
        boolean hr = ctx.isHr();
        List<ImprovementPlan> rows = plans.findAllByCompanyIdAndDeletedFalseOrderByStartDateDesc(ctx.companyId());
        Map<Long, Employee> people = ctx.people(rows.stream().flatMap(p -> java.util.stream.Stream.of(p.getEmployeeId(), p.getOwnerEmployeeId())).toList());
        return rows.stream().filter(p -> hr || (me != null && canManage(p, me, people))).map(p -> toDto(p, people, true)).toList();
    }

    @Transactional(readOnly = true)
    public List<PipDTO> mine() {
        Employee me = ctx.me();
        List<ImprovementPlan> rows = plans.findAllByCompanyIdAndEmployeeIdAndDeletedFalseOrderByStartDateDesc(ctx.companyId(), me.getId());
        Map<Long, Employee> people = ctx.people(rows.stream().flatMap(p -> java.util.stream.Stream.of(p.getEmployeeId(), p.getOwnerEmployeeId())).toList());
        return rows.stream().map(p -> toDto(p, people, false)).toList();
    }

    @Transactional
    public PipDTO create(PipRequest r, String actor) {
        Employee me = ctx.me();
        if (r == null || r.employeeId() == null) throw new BadRequestException("Choose the employee");
        Employee employee = ctx.employee(r.employeeId());
        if (employee.getId().equals(me.getId())) throw new BadRequestException("You can't open a plan for yourself");
        if (!ctx.isHr() && !ctx.manages(me.getId(), employee)) throw new AccessDeniedException("Only the reporting manager or HR can open a plan");
        if (r.startDate() == null || r.endDate() == null || !r.endDate().isAfter(r.startDate())) throw new BadRequestException("Choose a start and a later end date");
        String reason = PerfContext.text(r.reason(), 2000);
        if (reason == null) throw new BadRequestException("Explain why the plan is needed");
        List<Expectation> expectations = r.expectations() == null ? List.of() : r.expectations().stream()
                .filter(e -> e != null && PerfContext.text(e.goal(), 500) != null)
                .map(e -> new Expectation(e.goal().trim(), PerfContext.text(e.measure(), 500))).toList();
        if (expectations.isEmpty()) throw new BadRequestException("Add at least one expectation");
        boolean active = plans.findAllByCompanyIdAndEmployeeIdAndDeletedFalseOrderByStartDateDesc(ctx.companyId(), employee.getId()).stream()
                .anyMatch(p -> "ACTIVE".equals(p.getStatus()));
        if (active) throw new BadRequestException(employee.getFullName() + " already has an active plan");
        ImprovementPlan p = new ImprovementPlan();
        p.setCompanyId(ctx.companyId());
        p.setEmployeeId(employee.getId());
        p.setOwnerEmployeeId(me.getId());
        p.setStartDate(r.startDate());
        p.setEndDate(r.endDate());
        p.setReason(reason);
        p.setExpectationsJson(write(expectations));
        p.setCheckinsJson(write(List.of()));
        p.setStatus("ACTIVE");
        p = plans.save(p);
        ctx.notify(employee, "PERFORMANCE", "Performance improvement plan", "A plan running until " + r.endDate() + " has been shared with you. Please review and acknowledge it.", "IMPROVEMENT_PLAN", p.getId());
        auditLogService.log("IMPROVEMENT_PLAN", p.getId(), "CREATED", actor + " opened a plan for employee " + employee.getId());
        return toDto(p, ctx.people(List.of(employee.getId(), me.getId())), true);
    }

    @Transactional
    public PipDTO checkIn(Long id, CheckInRequest r) {
        Employee me = ctx.me();
        ImprovementPlan p = manageable(id, me);
        if (!"ACTIVE".equals(p.getStatus())) throw new BadRequestException("This plan is closed");
        String note = PerfContext.text(r == null ? null : r.note(), 2000);
        if (note == null) throw new BadRequestException("Write a note");
        String rating = r.rating() == null ? null : r.rating().toUpperCase(Locale.ROOT);
        if (rating != null && !Set.of("ON_TRACK", "SOME_PROGRESS", "NO_PROGRESS").contains(rating)) throw new BadRequestException("Unknown progress rating");
        List<CheckInNote> notes = new ArrayList<>(checkIns(p));
        notes.add(new CheckInNote(LocalDate.now(clock), me.getFullName(), note, rating));
        p.setCheckinsJson(write(notes));
        plans.save(p);
        return toDto(p, ctx.people(List.of(p.getEmployeeId(), p.getOwnerEmployeeId())), true);
    }

    @Transactional
    public PipDTO close(Long id, CloseRequest r, String actor) {
        Employee me = ctx.me();
        ImprovementPlan p = manageable(id, me);
        if (!"ACTIVE".equals(p.getStatus())) throw new BadRequestException("This plan is already closed");
        String outcome = r == null || r.outcome() == null ? "" : r.outcome().toUpperCase(Locale.ROOT);
        if (!OUTCOMES.contains(outcome)) throw new BadRequestException("Choose an outcome");
        p.setOutcomeNote(PerfContext.text(r.note(), 1000));
        if ("EXTENDED".equals(outcome)) {
            if (r.newEndDate() == null || !r.newEndDate().isAfter(p.getEndDate())) throw new BadRequestException("Choose a new end date after " + p.getEndDate());
            p.setEndDate(r.newEndDate());
        } else {
            p.setStatus(outcome);
        }
        plans.save(p);
        Map<Long, Employee> people = ctx.people(List.of(p.getEmployeeId(), p.getOwnerEmployeeId()));
        ctx.notify(people.get(p.getEmployeeId()), "PERFORMANCE", "Improvement plan update",
                "EXTENDED".equals(outcome) ? "Your plan has been extended to " + p.getEndDate() + "." : "Your plan has been closed.", "IMPROVEMENT_PLAN", p.getId());
        auditLogService.log("IMPROVEMENT_PLAN", p.getId(), outcome, actor + " closed/updated the plan: " + outcome);
        return toDto(p, people, true);
    }

    @Transactional
    public PipDTO acknowledge(Long id) {
        Employee me = ctx.me();
        ImprovementPlan p = plans.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        if (!p.getEmployeeId().equals(me.getId())) throw new AccessDeniedException("Not your plan");
        if (p.getAcknowledgedAt() == null) {
            p.setAcknowledgedAt(LocalDateTime.now(clock));
            plans.save(p);
        }
        return toDto(p, ctx.people(List.of(p.getEmployeeId(), p.getOwnerEmployeeId())), false);
    }

    // ------------------------------------------------------------------ helpers

    private ImprovementPlan manageable(Long id, Employee me) {
        ImprovementPlan p = plans.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        if (!ctx.isHr() && !canManage(p, me, ctx.people(List.of(p.getEmployeeId())))) throw new AccessDeniedException("You can't change this plan");
        return p;
    }

    private boolean canManage(ImprovementPlan p, Employee me, Map<Long, Employee> people) {
        if (me.getId().equals(p.getEmployeeId())) return false;
        if (me.getId().equals(p.getOwnerEmployeeId())) return true;
        Employee e = people.get(p.getEmployeeId());
        return e != null && ctx.manages(me.getId(), e);
    }

    private List<CheckInNote> checkIns(ImprovementPlan p) {
        if (p.getCheckinsJson() == null || p.getCheckinsJson().isBlank()) return List.of();
        try {
            return objectMapper.readValue(p.getCheckinsJson(), new TypeReference<List<CheckInNote>>() { });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Expectation> expectations(ImprovementPlan p) {
        try {
            return objectMapper.readValue(p.getExpectationsJson(), new TypeReference<List<Expectation>>() { });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not save the plan", ex);
        }
    }

    private PipDTO toDto(ImprovementPlan p, Map<Long, Employee> people, boolean canManage) {
        Employee e = people.get(p.getEmployeeId());
        return new PipDTO(p.getId(), p.getEmployeeId(), e == null ? null : e.getFullName(), e == null ? null : e.getEmployeeCode(),
                p.getOwnerEmployeeId(), PerfContext.name(people, p.getOwnerEmployeeId()), p.getStartDate(), p.getEndDate(), p.getReason(),
                expectations(p), checkIns(p), p.getStatus(), p.getOutcomeNote(), p.getAcknowledgedAt(), canManage);
    }
}
