package com.haodaone.performance.suite;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * OKRs: company and department objectives (HR) and personal objectives (the employee, their manager or HR), each with
 * measurable key results. Progress of a key result is how far the current value has moved from start to target; an
 * objective's progress is the average of its key results. Everyone in the company can see the OKRs for a period, so
 * people can align personal objectives to company ones.
 */
@Service
public class OkrService {

    public static final Set<String> LEVELS = Set.of("COMPANY", "DEPARTMENT", "PERSONAL");
    public static final Set<String> STATUSES = Set.of("ON_TRACK", "AT_RISK", "OFF_TRACK", "DONE", "DROPPED");

    private final OkrObjectiveRepository objectives;
    private final OkrKeyResultRepository keyResults;
    private final PerfContext ctx;

    public OkrService(OkrObjectiveRepository objectives, OkrKeyResultRepository keyResults, PerfContext ctx) {
        this.objectives = objectives;
        this.keyResults = keyResults;
        this.ctx = ctx;
    }

    public record ObjectiveRequest(String level, Long ownerEmployeeId, Long departmentId, String period, String title,
                                   String description, Long parentId, List<KeyResultRequest> keyResults) { }
    public record KeyResultRequest(String title, BigDecimal startValue, BigDecimal targetValue, String unit) { }
    public record CheckIn(BigDecimal currentValue, String note) { }
    public record KeyResultDTO(Long id, String title, BigDecimal startValue, BigDecimal targetValue, BigDecimal currentValue,
                               String unit, BigDecimal progress, String lastNote) { }
    public record ObjectiveDTO(Long id, String level, Long ownerEmployeeId, String ownerName, Long departmentId, String departmentName,
                               String period, String title, String description, Long parentId, String status, BigDecimal progress,
                               boolean canEdit, List<KeyResultDTO> keyResults) { }

    @Transactional(readOnly = true)
    public List<ObjectiveDTO> list(String period) {
        String p = period(period);
        List<OkrObjective> rows = objectives.findAllByCompanyIdAndPeriodAndDeletedFalseOrderByLevelAscIdAsc(ctx.companyId(), p);
        Map<Long, Employee> people = ctx.people(rows.stream().map(OkrObjective::getOwnerEmployeeId).toList());
        Employee me = ctx.meOrNull();
        boolean hr = ctx.isHr();
        Map<Long, String> departments = new HashMap<>();
        ctx.activeEmployees().forEach(e -> { if (e.getDepartment() != null) departments.put(e.getDepartment().getId(), e.getDepartment().getName()); });
        return rows.stream().map(o -> toDto(o, people, departments, canEdit(o, me, hr, people))).toList();
    }

    @Transactional
    public ObjectiveDTO create(ObjectiveRequest r) {
        if (r == null) throw new BadRequestException("Describe the objective");
        String level = r.level() == null ? "PERSONAL" : r.level().toUpperCase(Locale.ROOT);
        if (!LEVELS.contains(level)) throw new BadRequestException("Unknown level");
        Employee me = ctx.meOrNull();
        boolean hr = ctx.isHr();
        OkrObjective o = new OkrObjective();
        o.setCompanyId(ctx.companyId());
        o.setLevel(level);
        o.setPeriod(period(r.period()));
        o.setTitle(required(r.title(), 200, "Name the objective"));
        o.setDescription(PerfContext.text(r.description(), 1000));
        o.setStatus("ON_TRACK");
        o.setProgress(BigDecimal.ZERO);
        if ("PERSONAL".equals(level)) {
            Employee owner = r.ownerEmployeeId() == null ? me : ctx.employee(r.ownerEmployeeId());
            if (owner == null) throw new BadRequestException("Choose whose objective this is");
            boolean self = me != null && me.getId().equals(owner.getId());
            if (!self && !hr && !(me != null && ctx.manages(me.getId(), owner))) throw new AccessDeniedException("You can set objectives for yourself and your team");
            o.setOwnerEmployeeId(owner.getId());
        } else {
            if (!hr) throw new AccessDeniedException("Only HR can set company and department objectives");
            if ("DEPARTMENT".equals(level)) {
                if (r.departmentId() == null) throw new BadRequestException("Choose a department");
                o.setDepartmentId(r.departmentId());
            }
            o.setOwnerEmployeeId(r.ownerEmployeeId());
        }
        if (r.parentId() != null) {
            OkrObjective parent = objective(r.parentId());
            if (!parent.getPeriod().equals(o.getPeriod())) throw new BadRequestException("Align to an objective in the same period");
            o.setParentId(parent.getId());
        }
        o = objectives.save(o);
        if (r.keyResults() != null) for (KeyResultRequest k : r.keyResults()) addKeyResult(o, k);
        recompute(o);
        return get(o.getId());
    }

    @Transactional
    public ObjectiveDTO update(Long id, ObjectiveRequest r) {
        OkrObjective o = editable(id);
        if (r.title() != null) o.setTitle(required(r.title(), 200, "Name the objective"));
        o.setDescription(PerfContext.text(r.description(), 1000));
        objectives.save(o);
        return get(id);
    }

    @Transactional
    public ObjectiveDTO setStatus(Long id, String status) {
        OkrObjective o = editable(id);
        String s = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(s)) throw new BadRequestException("Unknown status");
        o.setStatus(s);
        objectives.save(o);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        OkrObjective o = editable(id);
        keyResults.findAllByObjectiveIdAndDeletedFalseOrderByIdAsc(o.getId()).forEach(k -> { k.setDeleted(true); keyResults.save(k); });
        o.setDeleted(true);
        objectives.save(o);
    }

    @Transactional
    public ObjectiveDTO addKeyResult(Long objectiveId, KeyResultRequest k) {
        OkrObjective o = editable(objectiveId);
        addKeyResult(o, k);
        recompute(o);
        return get(objectiveId);
    }

    @Transactional
    public ObjectiveDTO removeKeyResult(Long keyResultId) {
        OkrKeyResult k = keyResults.findByIdAndCompanyIdAndDeletedFalse(keyResultId, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Key result not found"));
        OkrObjective o = editable(k.getObjectiveId());
        k.setDeleted(true);
        keyResults.save(k);
        recompute(o);
        return get(o.getId());
    }

    @Transactional
    public ObjectiveDTO checkIn(Long keyResultId, CheckIn c) {
        OkrKeyResult k = keyResults.findByIdAndCompanyIdAndDeletedFalse(keyResultId, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Key result not found"));
        OkrObjective o = editable(k.getObjectiveId());
        if (c == null || c.currentValue() == null) throw new BadRequestException("Enter the current value");
        k.setCurrentValue(c.currentValue());
        k.setLastNote(PerfContext.text(c.note(), 500));
        k.setProgress(progress(k));
        keyResults.save(k);
        recompute(o);
        return get(o.getId());
    }

    @Transactional(readOnly = true)
    public ObjectiveDTO get(Long id) {
        OkrObjective o = objective(id);
        Map<Long, Employee> people = ctx.people(Collections.singletonList(o.getOwnerEmployeeId()));
        return toDto(o, people, new HashMap<>(), canEdit(o, ctx.meOrNull(), ctx.isHr(), people));
    }

    // ------------------------------------------------------------------ helpers

    static BigDecimal progress(OkrKeyResult k) {
        BigDecimal span = k.getTargetValue().subtract(k.getStartValue());
        if (span.signum() == 0) return k.getCurrentValue().compareTo(k.getTargetValue()) >= 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        BigDecimal pct = k.getCurrentValue().subtract(k.getStartValue()).multiply(BigDecimal.valueOf(100)).divide(span, 1, RoundingMode.HALF_UP);
        return pct.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }

    private void addKeyResult(OkrObjective o, KeyResultRequest r) {
        if (r == null) return;
        if (r.targetValue() == null) throw new BadRequestException("Every key result needs a target");
        OkrKeyResult k = new OkrKeyResult();
        k.setCompanyId(o.getCompanyId());
        k.setObjectiveId(o.getId());
        k.setTitle(required(r.title(), 200, "Name the key result"));
        k.setStartValue(r.startValue() == null ? BigDecimal.ZERO : r.startValue());
        k.setTargetValue(r.targetValue());
        k.setCurrentValue(k.getStartValue());
        k.setUnit(PerfContext.text(r.unit(), 20));
        k.setProgress(progress(k));
        keyResults.save(k);
    }

    private void recompute(OkrObjective o) {
        List<OkrKeyResult> krs = keyResults.findAllByObjectiveIdAndDeletedFalseOrderByIdAsc(o.getId());
        BigDecimal avg = krs.isEmpty() ? BigDecimal.ZERO : krs.stream().map(OkrKeyResult::getProgress).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(krs.size()), 1, RoundingMode.HALF_UP);
        o.setProgress(avg);
        objectives.save(o);
    }

    private boolean canEdit(OkrObjective o, Employee me, boolean hr, Map<Long, Employee> people) {
        if (hr) return true;
        if (me == null || !"PERSONAL".equals(o.getLevel())) return false;
        if (me.getId().equals(o.getOwnerEmployeeId())) return true;
        Employee owner = people.get(o.getOwnerEmployeeId());
        return owner != null && ctx.manages(me.getId(), owner);
    }

    private OkrObjective editable(Long id) {
        OkrObjective o = objective(id);
        if (!canEdit(o, ctx.meOrNull(), ctx.isHr(), ctx.people(Collections.singletonList(o.getOwnerEmployeeId()))))
            throw new AccessDeniedException("You can't change this objective");
        return o;
    }

    private OkrObjective objective(Long id) {
        return objectives.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Objective not found"));
    }

    private static String period(String period) {
        String p = period == null ? "" : period.trim().toUpperCase(Locale.ROOT);
        if (!p.matches("\\d{4}(-Q[1-4]|-H[12])?")) throw new BadRequestException("Use a period like 2026-Q4, 2026-H2 or 2026");
        return p;
    }

    private static String required(String value, int max, String message) {
        String v = PerfContext.text(value, max);
        if (v == null) throw new BadRequestException(message);
        return v;
    }

    private ObjectiveDTO toDto(OkrObjective o, Map<Long, Employee> people, Map<Long, String> departments, boolean canEdit) {
        List<KeyResultDTO> krs = keyResults.findAllByObjectiveIdAndDeletedFalseOrderByIdAsc(o.getId()).stream()
                .map(k -> new KeyResultDTO(k.getId(), k.getTitle(), k.getStartValue(), k.getTargetValue(), k.getCurrentValue(), k.getUnit(), k.getProgress(), k.getLastNote()))
                .toList();
        return new ObjectiveDTO(o.getId(), o.getLevel(), o.getOwnerEmployeeId(), PerfContext.name(people, o.getOwnerEmployeeId()), o.getDepartmentId(),
                departments.get(o.getDepartmentId()), o.getPeriod(), o.getTitle(), o.getDescription(), o.getParentId(), o.getStatus(), o.getProgress(), canEdit, krs);
    }
}
