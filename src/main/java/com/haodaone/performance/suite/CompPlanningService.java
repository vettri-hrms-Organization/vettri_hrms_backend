package com.haodaone.performance.suite;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.salary.dto.SalaryComponentsDTO;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.salary.revision.SalaryRevisionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Compensation (appraisal) planning. Payroll admins open a round with an effective date, a budget (% of the current
 * monthly payroll) and a merit guideline (rating -> raise %). Recommendations are generated for every employee with a
 * salary structure, using the final rating from a linked review cycle; admins adjust them against the budget and then
 * apply the round, which creates an ANNUAL_APPRAISAL salary revision per employee (arrears included when back-dated).
 */
@Service
public class CompPlanningService {

    static final Map<String, BigDecimal> DEFAULT_GUIDELINES = new TreeMap<>(Map.of(
            "1", BigDecimal.ZERO, "2", BigDecimal.valueOf(3), "3", BigDecimal.valueOf(6), "4", BigDecimal.valueOf(10), "5", BigDecimal.valueOf(15)));
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CompCycleRepository cycles;
    private final CompRecommendationRepository recommendations;
    private final CycleParticipantRepository participants;
    private final SalaryStructureRepository structures;
    private final SalaryRevisionService revisionService;
    private final PerfContext ctx;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public CompPlanningService(CompCycleRepository cycles, CompRecommendationRepository recommendations, CycleParticipantRepository participants,
                               SalaryStructureRepository structures, SalaryRevisionService revisionService, PerfContext ctx,
                               AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.cycles = cycles;
        this.recommendations = recommendations;
        this.participants = participants;
        this.structures = structures;
        this.revisionService = revisionService;
        this.ctx = ctx;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    public record CycleRequest(String name, LocalDate effectiveFrom, BigDecimal budgetPercent, Long performanceCycleId, Map<String, BigDecimal> guidelines) { }
    public record RecommendationUpdate(BigDecimal proposedPercent, String note) { }
    public record RecommendationDTO(Long id, Long employeeId, String employeeName, String employeeCode, String department, BigDecimal currentGross,
                                    BigDecimal rating, BigDecimal proposedPercent, BigDecimal newGross, BigDecimal increment, String note, Long revisionId) { }
    public record CycleDTO(Long id, String name, LocalDate effectiveFrom, BigDecimal budgetPercent, Long performanceCycleId, Map<String, BigDecimal> guidelines,
                           String status, String appliedByName, BigDecimal currentPayroll, BigDecimal budgetAmount, BigDecimal plannedIncrease,
                           BigDecimal plannedPercent, List<RecommendationDTO> recommendations) { }
    public record ApplyResult(int applied, int skipped, List<String> errors, CycleDTO cycle) { }

    @Transactional(readOnly = true)
    public List<CycleDTO> list() {
        requireAdmin();
        return cycles.findAllByCompanyIdAndDeletedFalseOrderByEffectiveFromDesc(ctx.companyId()).stream().map(c -> toDto(c, false)).toList();
    }

    @Transactional(readOnly = true)
    public CycleDTO get(Long id) {
        requireAdmin();
        return toDto(cycle(id), true);
    }

    @Transactional
    public CycleDTO create(CycleRequest r, String actor) {
        requireAdmin();
        if (r == null || PerfContext.text(r.name(), 120) == null) throw new BadRequestException("Name the round");
        if (r.effectiveFrom() == null) throw new BadRequestException("Choose the effective date");
        if (r.budgetPercent() == null || r.budgetPercent().signum() < 0 || r.budgetPercent().compareTo(HUNDRED) > 0) throw new BadRequestException("Budget must be 0-100%");
        CompCycle c = new CompCycle();
        c.setCompanyId(ctx.companyId());
        c.setName(r.name().trim());
        c.setEffectiveFrom(r.effectiveFrom());
        c.setBudgetPercent(r.budgetPercent());
        c.setGuidelinesJson(write(guidelines(r.guidelines())));
        c.setStatus("DRAFT");
        if (r.performanceCycleId() != null) {
            if (participants.findAllByCycleIdAndDeletedFalseOrderByIdAsc(r.performanceCycleId()).stream().noneMatch(p -> ctx.companyId().equals(p.getCompanyId())))
                throw new BadRequestException("That review cycle has nobody in it");
            c.setPerformanceCycleId(r.performanceCycleId());
        }
        c = cycles.save(c);
        generate(c);
        auditLogService.log("COMP_CYCLE", c.getId(), "CREATED", actor + " opened compensation round '" + c.getName() + "'");
        return toDto(c, true);
    }

    /** Re-applies the guideline to every recommendation (manual edits are replaced). */
    @Transactional
    public CycleDTO regenerate(Long id, Map<String, BigDecimal> newGuidelines) {
        requireAdmin();
        CompCycle c = draft(id);
        if (newGuidelines != null) c.setGuidelinesJson(write(guidelines(newGuidelines)));
        cycles.save(c);
        recommendations.findAllByCompCycleIdAndDeletedFalseOrderByIdAsc(c.getId()).forEach(x -> { x.setDeleted(true); recommendations.save(x); });
        generate(c);
        return toDto(c, true);
    }

    @Transactional
    public CycleDTO updateRecommendation(Long cycleId, Long recommendationId, RecommendationUpdate u) {
        requireAdmin();
        CompCycle c = draft(cycleId);
        CompRecommendation x = recommendations.findByIdAndCompCycleIdAndDeletedFalse(recommendationId, c.getId()).orElseThrow(() -> new ResourceNotFoundException("Recommendation not found"));
        if (u == null || u.proposedPercent() == null || u.proposedPercent().signum() < 0 || u.proposedPercent().compareTo(HUNDRED) > 0)
            throw new BadRequestException("Raise must be 0-100%");
        x.setProposedPercent(u.proposedPercent().setScale(2, RoundingMode.HALF_UP));
        x.setNewGross(newGross(x.getCurrentGross(), x.getProposedPercent()));
        x.setNote(PerfContext.text(u.note(), 500));
        recommendations.save(x);
        return toDto(c, true);
    }

    /**
     * Creates a salary revision for every recommendation with a raise. Not one transaction: each revision commits on
     * its own, so one employee's problem (no structure, an open revision) doesn't undo everyone else's raise.
     */
    public ApplyResult apply(Long id, String actor) {
        requireAdmin();
        CompCycle c = draft(id);
        Employee me = ctx.meOrNull();
        int applied = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        Map<Long, Employee> people = ctx.people(recommendations.findAllByCompCycleIdAndDeletedFalseOrderByIdAsc(c.getId()).stream().map(CompRecommendation::getEmployeeId).toList());
        for (CompRecommendation x : recommendations.findAllByCompCycleIdAndDeletedFalseOrderByIdAsc(c.getId())) {
            if (x.getRevisionId() != null) continue;
            String name = Optional.ofNullable(PerfContext.name(people, x.getEmployeeId())).orElse("Employee " + x.getEmployeeId());
            if (x.getProposedPercent().signum() == 0) { skipped++; continue; }
            if (me != null && me.getId().equals(x.getEmployeeId())) { errors.add(name + ": your own raise must be applied by a colleague"); skipped++; continue; }
            SalaryStructure current = structures.findByEmployeeIdAndActiveTrueAndDeletedFalse(x.getEmployeeId()).orElse(null);
            if (current == null) { errors.add(name + ": no salary structure"); skipped++; continue; }
            try {
                SalaryComponentsDTO components = SalaryComponentsDTO.from(raise(current.getComponents(), x.getProposedPercent()));
                var revision = revisionService.create(new SalaryRevisionService.RevisionRequest(x.getEmployeeId(), "ANNUAL_APPRAISAL", c.getEffectiveFrom(),
                        components, null, c.getName() + " - " + x.getProposedPercent().stripTrailingZeros().toPlainString() + "%", null), actor);
                x.setRevisionId(revision.id());
                recommendations.save(x);
                applied++;
            } catch (BadRequestException | AccessDeniedException ex) {
                errors.add(name + ": " + ex.getMessage());
                skipped++;
            }
        }
        boolean pending = recommendations.findAllByCompCycleIdAndDeletedFalseOrderByIdAsc(c.getId()).stream()
                .anyMatch(x -> x.getRevisionId() == null && x.getProposedPercent().signum() > 0);
        if (!pending) {
            c.setStatus("APPLIED");
            c.setAppliedByName(actor);
            c = cycles.save(c);
        }
        auditLogService.log("COMP_CYCLE", c.getId(), "APPLIED", actor + " applied '" + c.getName() + "': " + applied + " revision(s), " + skipped + " skipped");
        return new ApplyResult(applied, skipped, errors, toDto(c, true));
    }

    // ------------------------------------------------------------------ helpers

    /** Raises the fixed earnings (basic, HRA, special, medical, travel) so that gross goes up by {@code percent} of today's gross. */
    static SalaryComponents raise(SalaryComponents current, BigDecimal percent) {
        BigDecimal gross = current.grossSalary();
        BigDecimal increment = gross.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal fixed = nz(current.getBasicSalary()).add(nz(current.getHra())).add(nz(current.getSpecialAllowance()))
                .add(nz(current.getMedicalAllowance())).add(nz(current.getTravelAllowance()));
        SalaryComponents next = SalaryComponentsDTO.from(current).toEntity();
        if (fixed.signum() == 0) {
            next.setBasicSalary(increment);
            return next;
        }
        BigDecimal factor = fixed.add(increment).divide(fixed, 10, RoundingMode.HALF_UP);
        next.setBasicSalary(scale(current.getBasicSalary(), factor));
        next.setHra(scale(current.getHra(), factor));
        next.setMedicalAllowance(scale(current.getMedicalAllowance(), factor));
        next.setTravelAllowance(scale(current.getTravelAllowance(), factor));
        BigDecimal others = next.getBasicSalary().add(next.getHra()).add(next.getMedicalAllowance()).add(next.getTravelAllowance());
        next.setSpecialAllowance(fixed.add(increment).subtract(others).max(BigDecimal.ZERO));
        return next;
    }

    private void generate(CompCycle c) {
        Map<String, BigDecimal> guide = guidelines(c);
        Map<Long, BigDecimal> ratings = new HashMap<>();
        if (c.getPerformanceCycleId() != null) {
            participants.findAllByCycleIdAndDeletedFalseOrderByIdAsc(c.getPerformanceCycleId()).forEach(p -> {
                BigDecimal r = p.getFinalRating() != null ? p.getFinalRating() : p.getManagerRating();
                if (r != null) ratings.put(p.getEmployeeId(), r);
            });
        }
        for (Employee e : ctx.activeEmployees()) {
            SalaryStructure s = structures.findByEmployeeIdAndActiveTrueAndDeletedFalse(e.getId()).orElse(null);
            if (s == null) continue;
            CompRecommendation x = new CompRecommendation();
            x.setCompanyId(c.getCompanyId());
            x.setCompCycleId(c.getId());
            x.setEmployeeId(e.getId());
            x.setCurrentGross(s.getGrossSalary());
            BigDecimal rating = ratings.get(e.getId());
            x.setRating(rating);
            BigDecimal pct = rating == null ? BigDecimal.ZERO
                    : guide.getOrDefault(String.valueOf(rating.setScale(0, RoundingMode.HALF_UP).intValue()), BigDecimal.ZERO);
            x.setProposedPercent(pct.setScale(2, RoundingMode.HALF_UP));
            x.setNewGross(newGross(x.getCurrentGross(), x.getProposedPercent()));
            recommendations.save(x);
        }
    }

    private static BigDecimal newGross(BigDecimal current, BigDecimal pct) {
        return current.add(current.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP));
    }

    private static BigDecimal scale(BigDecimal v, BigDecimal factor) {
        return nz(v).multiply(factor).setScale(0, RoundingMode.HALF_UP).setScale(2);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static Map<String, BigDecimal> guidelines(Map<String, BigDecimal> requested) {
        Map<String, BigDecimal> out = new TreeMap<>(DEFAULT_GUIDELINES);
        if (requested != null) requested.forEach((k, v) -> {
            if (!DEFAULT_GUIDELINES.containsKey(k)) throw new BadRequestException("Guidelines are for ratings 1 to 5");
            if (v == null || v.signum() < 0 || v.compareTo(HUNDRED) > 0) throw new BadRequestException("Guideline raises must be 0-100%");
            out.put(k, v);
        });
        return out;
    }

    private Map<String, BigDecimal> guidelines(CompCycle c) {
        try {
            return new TreeMap<>(objectMapper.readValue(c.getGuidelinesJson(), new TypeReference<Map<String, BigDecimal>>() { }));
        } catch (Exception ex) {
            return new TreeMap<>(DEFAULT_GUIDELINES);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not save", ex);
        }
    }

    private void requireAdmin() {
        if (!ctx.hasOrgScope("SALARY_MANAGE")) throw new AccessDeniedException("Only payroll admins can plan compensation");
    }

    private CompCycle cycle(Long id) {
        return cycles.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Compensation round not found"));
    }

    private CompCycle draft(Long id) {
        CompCycle c = cycle(id);
        if (!"DRAFT".equals(c.getStatus())) throw new BadRequestException("This round has already been applied");
        return c;
    }

    private CycleDTO toDto(CompCycle c, boolean withRows) {
        List<CompRecommendation> rows = recommendations.findAllByCompCycleIdAndDeletedFalseOrderByIdAsc(c.getId());
        BigDecimal payroll = rows.stream().map(CompRecommendation::getCurrentGross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal increase = rows.stream().map(x -> x.getNewGross().subtract(x.getCurrentGross())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal budget = payroll.multiply(c.getBudgetPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal planned = payroll.signum() == 0 ? BigDecimal.ZERO : increase.multiply(HUNDRED).divide(payroll, 2, RoundingMode.HALF_UP);
        List<RecommendationDTO> dtos = List.of();
        if (withRows) {
            Map<Long, Employee> people = ctx.people(rows.stream().map(CompRecommendation::getEmployeeId).toList());
            dtos = rows.stream().map(x -> {
                Employee e = people.get(x.getEmployeeId());
                return new RecommendationDTO(x.getId(), x.getEmployeeId(), e == null ? null : e.getFullName(), e == null ? null : e.getEmployeeCode(),
                        e == null || e.getDepartment() == null ? null : e.getDepartment().getName(), x.getCurrentGross(), x.getRating(),
                        x.getProposedPercent(), x.getNewGross(), x.getNewGross().subtract(x.getCurrentGross()), x.getNote(), x.getRevisionId());
            }).toList();
        }
        return new CycleDTO(c.getId(), c.getName(), c.getEffectiveFrom(), c.getBudgetPercent(), c.getPerformanceCycleId(), guidelines(c), c.getStatus(),
                c.getAppliedByName(), payroll, budget, increase, planned, dtos);
    }
}
