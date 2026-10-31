package com.haodaone.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Multi-level approval chains. A company configures, per module, an ordered list of levels:
 * REPORTING_MANAGER, SKIP_LEVEL_MANAGER (the manager's manager) and HR (anyone with the module's approve permission at
 * organization scope). Levels that can't be resolved for an employee (no manager, no skip-level manager, or the level's
 * person is the applicant) are dropped. Optionally the levels after the first only apply above a number of days.
 * With no workflow configured, modules keep their single-step behaviour.
 */
@Service
public class ApprovalWorkflowService {

    public static final String LEAVE = "LEAVE";
    public static final List<String> LEVELS = List.of("REPORTING_MANAGER", "SKIP_LEVEL_MANAGER", "HR");
    private static final Map<String, String> LABELS = Map.of("REPORTING_MANAGER", "Reporting manager", "SKIP_LEVEL_MANAGER", "Skip-level manager", "HR", "HR");
    private static final Map<String, String> PERMISSIONS = Map.of(LEAVE, "LEAVE_APPROVE");
    private static final Map<String, String> MANAGE_PERMISSIONS = Map.of(LEAVE, "LEAVE_MANAGE");

    private final ApprovalWorkflowRepository workflows;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public ApprovalWorkflowService(ApprovalWorkflowRepository workflows, AuthorizationService authorizationService,
                                   AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.workflows = workflows;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    /** One resolved step: who decides it (null approverEmployeeId = HR). */
    public record Level(String code, String label, Long approverEmployeeId, String approverName) {
        public String pendingWith() {
            return approverName == null ? label : approverName + " (" + label.toLowerCase(Locale.ROOT) + ")";
        }
    }

    public record TrailEntry(int level, String levelLabel, String decidedBy, String decision, String at, String note) { }
    public record WorkflowView(String module, List<String> levels, BigDecimal fullChainMinDays, boolean active) { }
    public record WorkflowRequest(List<String> levels, BigDecimal fullChainMinDays, Boolean active) { }

    /** What an approval did: advanced to another level, or was the final one. */
    public record Outcome(boolean finalApproval, Level next, String trailJson) { }

    @Transactional(readOnly = true)
    public WorkflowView get(String module) {
        String m = module(module);
        return workflows.findFirstByCompanyIdAndModuleAndDeletedFalse(tenant(), m)
                .map(w -> new WorkflowView(m, levels(w), w.getFullChainMinDays(), w.isActive()))
                .orElse(new WorkflowView(m, List.of(), null, false));
    }

    @Transactional
    public WorkflowView save(String module, WorkflowRequest r, String actor) {
        String m = module(module);
        if (!authorizationService.hasOrganizationScope(MANAGE_PERMISSIONS.get(m))) throw new AccessDeniedException("Only admins can change approval workflows");
        List<String> levels = r == null || r.levels() == null ? List.of() : r.levels().stream().map(x -> x == null ? "" : x.trim().toUpperCase(Locale.ROOT)).toList();
        if (levels.size() > 3) throw new BadRequestException("Use at most three levels");
        if (levels.stream().anyMatch(x -> !LEVELS.contains(x))) throw new BadRequestException("Unknown approval level");
        if (new HashSet<>(levels).size() != levels.size()) throw new BadRequestException("Each level can appear once");
        if (r.fullChainMinDays() != null && r.fullChainMinDays().signum() < 0) throw new BadRequestException("Days can't be negative");
        ApprovalWorkflow w = workflows.findFirstByCompanyIdAndModuleAndDeletedFalse(tenant(), m).orElseGet(ApprovalWorkflow::new);
        w.setCompanyId(tenant());
        w.setModule(m);
        w.setLevelsJson(write(levels));
        w.setFullChainMinDays(r.fullChainMinDays());
        w.setActive(levels.size() > 1 && !Boolean.FALSE.equals(r.active()));
        w = workflows.save(w);
        auditLogService.log("APPROVAL_WORKFLOW", w.getId(), "UPDATED", actor + " set the " + m + " approval chain to " + (levels.isEmpty() ? "single step" : String.join(" -> ", levels)));
        return new WorkflowView(m, levels, w.getFullChainMinDays(), w.isActive());
    }

    /** The resolved chain for one request, or an empty list when the module uses single-step approval. */
    @Transactional(readOnly = true)
    public List<Level> chainFor(String module, Long companyId, Employee applicant, double days) {
        ApprovalWorkflow w = workflows.findFirstByCompanyIdAndModuleAndDeletedFalse(companyId, module).orElse(null);
        if (w == null || !w.isActive()) return List.of();
        List<String> codes = levels(w);
        if (w.getFullChainMinDays() != null && days <= w.getFullChainMinDays().doubleValue() && !codes.isEmpty()) codes = codes.subList(0, 1);
        List<Level> out = new ArrayList<>();
        for (String code : codes) {
            Employee approver = switch (code) {
                case "REPORTING_MANAGER" -> applicant.getReportingManager();
                case "SKIP_LEVEL_MANAGER" -> applicant.getReportingManager() == null ? null : applicant.getReportingManager().getReportingManager();
                default -> null;
            };
            if (!"HR".equals(code) && (approver == null || approver.getId().equals(applicant.getId()))) continue;
            if (approver != null && out.stream().anyMatch(l -> approver.getId().equals(l.approverEmployeeId()))) continue;
            out.add(new Level(code, LABELS.get(code), approver == null ? null : approver.getId(), approver == null ? null : approver.getFullName()));
        }
        return out.size() > 1 ? out : List.of();
    }

    /** True when {@code me} decides the request's current level (HR with organization scope can always decide). */
    public boolean canAct(String module, List<Level> chain, int level, Employee me) {
        if (authorizationService.hasOrganizationScope(PERMISSIONS.get(module))) return true;
        if (chain.isEmpty() || me == null) return false;
        Level current = chain.get(Math.min(level, chain.size() - 1));
        return current.approverEmployeeId() != null && current.approverEmployeeId().equals(me.getId());
    }

    /**
     * Records an approval at the current level. HR at organization scope finishes the chain (an override, recorded as
     * such); anyone else must be the current level's approver and the request moves to the next level.
     */
    public Outcome approve(String module, List<Level> chain, int level, String trailJson, Employee me, String note) {
        boolean hr = authorizationService.hasOrganizationScope(PERMISSIONS.get(module));
        int at = Math.min(level, chain.size() - 1);
        Level current = chain.get(at);
        if (!hr && (me == null || current.approverEmployeeId() == null || !current.approverEmployeeId().equals(me.getId()))) {
            throw new AccessDeniedException("This request is waiting for " + current.pendingWith());
        }
        boolean override = hr && (current.approverEmployeeId() == null || me == null || !current.approverEmployeeId().equals(me.getId())) && !"HR".equals(current.code());
        String trail = append(trailJson, new TrailEntry(at, current.label(), me == null ? "HR" : me.getFullName(), override ? "APPROVED (HR override)" : "APPROVED",
                LocalDateTime.now().withNano(0).toString(), note));
        boolean last = hr || at + 1 >= chain.size();
        return new Outcome(last, last ? null : chain.get(at + 1), trail);
    }

    public String reject(List<Level> chain, int level, String trailJson, Employee me, String note) {
        int at = chain.isEmpty() ? 0 : Math.min(level, chain.size() - 1);
        String label = chain.isEmpty() ? "Approver" : chain.get(at).label();
        return append(trailJson, new TrailEntry(at, label, me == null ? "HR" : me.getFullName(), "REJECTED", LocalDateTime.now().withNano(0).toString(), note));
    }

    // ------------------------------------------------------------------ helpers

    private String append(String trailJson, TrailEntry entry) {
        List<TrailEntry> trail = new ArrayList<>();
        if (trailJson != null && !trailJson.isBlank()) {
            try {
                trail.addAll(objectMapper.readValue(trailJson, new TypeReference<List<TrailEntry>>() { }));
            } catch (Exception ignored) {
                // A damaged trail is restarted rather than blocking the approval.
            }
        }
        trail.add(entry);
        return write(trail);
    }

    private List<String> levels(ApprovalWorkflow w) {
        try {
            return objectMapper.readValue(w.getLevelsJson(), new TypeReference<List<String>>() { });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String write(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not save the approval chain", ex);
        }
    }

    private static String module(String module) {
        String m = module == null ? "" : module.trim().toUpperCase(Locale.ROOT);
        if (!PERMISSIONS.containsKey(m)) throw new BadRequestException("Unknown module");
        return m;
    }

    private static Long tenant() {
        Long id = TenantContext.getCurrentTenant();
        if (id == null) throw new BadRequestException("Company context is required");
        return id;
    }
}
