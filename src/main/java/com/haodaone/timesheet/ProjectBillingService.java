package com.haodaone.timesheet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.performance.suite.PerfContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Project billing (PSA). Projects carry an hourly billing rate with optional per-person overrides. Invoices are built
 * from billable hours on approved timesheet weeks that haven't been invoiced yet; each entry is stamped with its
 * invoice so it is never billed twice (voiding an invoice releases them). Utilization shows billable vs total hours
 * per person and hours vs budget per project. Needs TIMESHEET_MANAGE at organization scope.
 */
@Service
public class ProjectBillingService {

    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    private final TimesheetProjectRepository projects;
    private final TimesheetEntryRepository entries;
    private final TimesheetWeekRepository weeks;
    private final ProjectInvoiceRepository invoices;
    private final PerfContext ctx;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProjectBillingService(TimesheetProjectRepository projects, TimesheetEntryRepository entries, TimesheetWeekRepository weeks,
                                 ProjectInvoiceRepository invoices, PerfContext ctx, AuditLogService auditLogService, ObjectMapper objectMapper,
                                 Clock applicationClock) {
        this.projects = projects;
        this.entries = entries;
        this.weeks = weeks;
        this.invoices = invoices;
        this.ctx = ctx;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = applicationClock;
    }

    public record RatesRequest(BigDecimal billingRate, Map<Long, BigDecimal> memberRates) { }
    public record ProjectRates(Long projectId, String projectName, String clientName, boolean billable, BigDecimal billingRate,
                               Map<Long, BigDecimal> memberRates, Integer budgetMinutes) { }
    public record Line(Long employeeId, String employeeName, BigDecimal hours, BigDecimal rate, BigDecimal amount) { }
    public record InvoiceRequest(Long projectId, LocalDate from, LocalDate to, BigDecimal taxPercent, String notes) { }
    public record InvoiceDTO(Long id, String invoiceNumber, Long projectId, String projectName, String clientName, LocalDate periodFrom, LocalDate periodTo,
                             String status, List<Line> lines, BigDecimal subtotal, BigDecimal taxPercent, BigDecimal taxAmount, BigDecimal total,
                             String notes, String createdByName, LocalDateTime createdAt, LocalDateTime sentAt, LocalDateTime paidAt) { }
    public record Preview(Long projectId, String projectName, List<Line> lines, BigDecimal subtotal, BigDecimal unapprovedHours, List<String> warnings) { }
    public record PersonUtilization(Long employeeId, String employeeName, String department, BigDecimal totalHours, BigDecimal billableHours, BigDecimal utilizationPercent) { }
    public record ProjectUtilization(Long projectId, String projectName, String clientName, BigDecimal hours, BigDecimal billableHours,
                                     BigDecimal budgetHours, BigDecimal budgetUsedPercent, BigDecimal billableValue) { }
    public record Utilization(LocalDate from, LocalDate to, List<PersonUtilization> people, List<ProjectUtilization> projects) { }

    // ------------------------------------------------------------------ rates

    @Transactional(readOnly = true)
    public List<ProjectRates> rates() {
        requireManager();
        return projects.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(ctx.companyId()).stream().map(this::rates).toList();
    }

    @Transactional
    public ProjectRates setRates(Long projectId, RatesRequest r, String actor) {
        requireManager();
        TimesheetProject p = project(projectId);
        if (r == null) throw new BadRequestException("Enter a rate");
        if (r.billingRate() != null && r.billingRate().signum() < 0) throw new BadRequestException("Rates can't be negative");
        Map<Long, BigDecimal> members = r.memberRates() == null ? Map.of() : r.memberRates().entrySet().stream()
                .filter(e -> e.getValue() != null).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (members.values().stream().anyMatch(v -> v.signum() < 0)) throw new BadRequestException("Rates can't be negative");
        if (!members.isEmpty() && !ctx.people(members.keySet()).keySet().containsAll(members.keySet())) throw new BadRequestException("Unknown employee in the rates");
        p.setBillingRate(r.billingRate());
        p.setMemberRatesJson(members.isEmpty() ? null : write(members));
        projects.save(p);
        auditLogService.log("TIMESHEET_PROJECT", p.getId(), "RATES", actor + " set billing rate " + r.billingRate() + " on " + p.getName());
        return rates(p);
    }

    // ------------------------------------------------------------------ invoices

    @Transactional(readOnly = true)
    public Preview preview(InvoiceRequest r) {
        requireManager();
        TimesheetProject p = project(r == null ? null : r.projectId());
        Build b = build(p, r);
        return new Preview(p.getId(), p.getName(), b.lines(), b.subtotal(), b.unapprovedHours(), b.warnings());
    }

    @Transactional
    public InvoiceDTO create(InvoiceRequest r, String actor) {
        requireManager();
        TimesheetProject p = project(r == null ? null : r.projectId());
        Build b = build(p, r);
        if (b.entries().isEmpty()) throw new BadRequestException("No approved, un-invoiced billable hours in this period");
        BigDecimal tax = r.taxPercent() == null ? BigDecimal.ZERO : r.taxPercent();
        if (tax.signum() < 0 || tax.compareTo(BigDecimal.valueOf(100)) > 0) throw new BadRequestException("Tax must be 0-100%");
        ProjectInvoice inv = new ProjectInvoice();
        inv.setCompanyId(ctx.companyId());
        inv.setProjectId(p.getId());
        inv.setInvoiceNumber("INV-" + LocalDate.now(clock).getYear() + "-" + String.format("%04d", invoices.countByCompanyId(ctx.companyId()) + 1));
        inv.setPeriodFrom(r.from());
        inv.setPeriodTo(r.to());
        inv.setStatus(ProjectInvoice.DRAFT);
        inv.setLinesJson(write(b.lines()));
        inv.setSubtotal(b.subtotal());
        inv.setTaxPercent(tax);
        inv.setTaxAmount(b.subtotal().multiply(tax).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        inv.setTotal(inv.getSubtotal().add(inv.getTaxAmount()));
        inv.setNotes(PerfContext.text(r.notes(), 1000));
        inv.setCreatedByName(actor);
        inv = invoices.save(inv);
        for (TimesheetEntry e : b.entries()) {
            e.setInvoiceId(inv.getId());
            entries.save(e);
        }
        auditLogService.log("PROJECT_INVOICE", inv.getId(), "CREATED", actor + " created " + inv.getInvoiceNumber() + " for " + p.getName() + ": " + inv.getTotal());
        return toDto(inv, p);
    }

    @Transactional(readOnly = true)
    public List<InvoiceDTO> invoices() {
        requireManager();
        Map<Long, TimesheetProject> byId = projects.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(ctx.companyId()).stream().collect(Collectors.toMap(TimesheetProject::getId, x -> x));
        return invoices.findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(ctx.companyId()).stream().map(i -> toDto(i, byId.get(i.getProjectId()))).toList();
    }

    @Transactional
    public InvoiceDTO setStatus(Long id, String status, String actor) {
        requireManager();
        ProjectInvoice inv = invoices.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        String s = status == null ? "" : status.toUpperCase(Locale.ROOT);
        switch (s) {
            case ProjectInvoice.SENT -> {
                if (!ProjectInvoice.DRAFT.equals(inv.getStatus())) throw new BadRequestException("Only drafts can be marked sent");
                inv.setSentAt(LocalDateTime.now(clock));
            }
            case ProjectInvoice.PAID -> {
                if (!ProjectInvoice.SENT.equals(inv.getStatus()) && !ProjectInvoice.DRAFT.equals(inv.getStatus())) throw new BadRequestException("This invoice can't be marked paid");
                inv.setPaidAt(LocalDateTime.now(clock));
            }
            case ProjectInvoice.VOID -> {
                if (ProjectInvoice.PAID.equals(inv.getStatus())) throw new BadRequestException("A paid invoice can't be voided");
                // Release the hours so they can be invoiced again.
                entries.findAllByCompanyIdAndProjectIdAndDeletedFalse(ctx.companyId(), inv.getProjectId()).stream()
                        .filter(e -> inv.getId().equals(e.getInvoiceId())).forEach(e -> { e.setInvoiceId(null); entries.save(e); });
            }
            default -> throw new BadRequestException("Unknown status");
        }
        inv.setStatus(s);
        invoices.save(inv);
        auditLogService.log("PROJECT_INVOICE", inv.getId(), s, actor + " marked " + inv.getInvoiceNumber() + " " + s.toLowerCase(Locale.ROOT));
        return toDto(inv, projects.findById(inv.getProjectId()).orElse(null));
    }

    // ------------------------------------------------------------------ utilization

    @Transactional(readOnly = true)
    public Utilization utilization(LocalDate from, LocalDate to) {
        requireManager();
        if (from == null || to == null || to.isBefore(from)) throw new BadRequestException("Choose a valid period");
        if (ChronoUnit.DAYS.between(from, to) > 366) throw new BadRequestException("Choose at most a year");
        List<TimesheetEntry> rows = entries.findAllByCompanyIdAndWorkDateBetweenAndDeletedFalse(ctx.companyId(), from, to);
        Map<Long, Employee> people = ctx.people(rows.stream().map(TimesheetEntry::getEmployeeId).collect(Collectors.toSet()));
        Map<Long, TimesheetProject> byId = projects.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(ctx.companyId()).stream().collect(Collectors.toMap(TimesheetProject::getId, x -> x));

        List<PersonUtilization> persons = rows.stream().collect(Collectors.groupingBy(TimesheetEntry::getEmployeeId)).entrySet().stream().map(e -> {
            int total = e.getValue().stream().mapToInt(TimesheetEntry::getMinutes).sum();
            int billable = e.getValue().stream().filter(TimesheetEntry::isBillable).mapToInt(TimesheetEntry::getMinutes).sum();
            Employee emp = people.get(e.getKey());
            return new PersonUtilization(e.getKey(), emp == null ? null : emp.getFullName(), emp == null || emp.getDepartment() == null ? null : emp.getDepartment().getName(),
                    hours(total), hours(billable), total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(billable * 100.0 / total).setScale(1, RoundingMode.HALF_UP));
        }).sorted(Comparator.comparing(PersonUtilization::utilizationPercent).reversed()).toList();

        List<ProjectUtilization> projectRows = rows.stream().collect(Collectors.groupingBy(TimesheetEntry::getProjectId)).entrySet().stream().map(e -> {
            TimesheetProject p = byId.get(e.getKey());
            int total = e.getValue().stream().mapToInt(TimesheetEntry::getMinutes).sum();
            int billable = e.getValue().stream().filter(TimesheetEntry::isBillable).mapToInt(TimesheetEntry::getMinutes).sum();
            Map<Long, BigDecimal> overrides = p == null ? Map.of() : memberRates(p);
            BigDecimal value = e.getValue().stream().filter(TimesheetEntry::isBillable).map(t -> {
                BigDecimal rate = p == null ? null : overrides.getOrDefault(t.getEmployeeId(), p.getBillingRate());
                return rate == null ? BigDecimal.ZERO : rate.multiply(BigDecimal.valueOf(t.getMinutes())).divide(SIXTY, 2, RoundingMode.HALF_UP);
            }).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal budget = p == null || p.getBudgetMinutes() == null ? null : hours(p.getBudgetMinutes());
            BigDecimal used = budget == null || budget.signum() == 0 ? null : hours(total).multiply(BigDecimal.valueOf(100)).divide(budget, 1, RoundingMode.HALF_UP);
            return new ProjectUtilization(e.getKey(), p == null ? null : p.getName(), p == null ? null : p.getClientName(), hours(total), hours(billable), budget, used, value);
        }).sorted(Comparator.comparing(ProjectUtilization::hours).reversed()).toList();
        return new Utilization(from, to, persons, projectRows);
    }

    // ------------------------------------------------------------------ helpers

    private record Build(List<TimesheetEntry> entries, List<Line> lines, BigDecimal subtotal, BigDecimal unapprovedHours, List<String> warnings) { }

    private Build build(TimesheetProject p, InvoiceRequest r) {
        if (r.from() == null || r.to() == null || r.to().isBefore(r.from())) throw new BadRequestException("Choose the billing period");
        if (!p.isBillable()) throw new BadRequestException(p.getName() + " is not a billable project");
        Map<Long, BigDecimal> overrides = memberRates(p);
        Map<String, Boolean> approved = new HashMap<>();
        List<TimesheetEntry> billable = new ArrayList<>();
        int unapproved = 0;
        for (TimesheetEntry e : entries.findAllByCompanyIdAndProjectIdAndDeletedFalse(ctx.companyId(), p.getId())) {
            if (!e.isBillable() || e.getInvoiceId() != null || e.getWorkDate().isBefore(r.from()) || e.getWorkDate().isAfter(r.to())) continue;
            LocalDate week = TimesheetService.weekStart(e.getWorkDate());
            boolean ok = approved.computeIfAbsent(e.getEmployeeId() + ":" + week, k -> weeks.findByEmployeeIdAndWeekStartAndDeletedFalse(e.getEmployeeId(), week)
                    .map(w -> TimesheetWeek.APPROVED.equals(w.getStatus())).orElse(false));
            if (ok) billable.add(e); else unapproved += e.getMinutes();
        }
        Map<Long, Employee> people = ctx.people(billable.stream().map(TimesheetEntry::getEmployeeId).collect(Collectors.toSet()));
        List<String> warnings = new ArrayList<>();
        List<Line> lines = billable.stream().collect(Collectors.groupingBy(TimesheetEntry::getEmployeeId, TreeMap::new, Collectors.summingInt(TimesheetEntry::getMinutes)))
                .entrySet().stream().map(e -> {
                    BigDecimal rate = overrides.getOrDefault(e.getKey(), p.getBillingRate());
                    Employee emp = people.get(e.getKey());
                    String name = emp == null ? "Employee " + e.getKey() : emp.getFullName();
                    if (rate == null) warnings.add(name + " has no rate - set the project rate first");
                    BigDecimal amount = rate == null ? BigDecimal.ZERO : rate.multiply(BigDecimal.valueOf(e.getValue())).divide(SIXTY, 2, RoundingMode.HALF_UP);
                    return new Line(e.getKey(), name, hours(e.getValue()), rate, amount);
                }).toList();
        if (unapproved > 0) warnings.add(hours(unapproved).toPlainString() + " billable hours are on weeks not yet approved and are left out");
        BigDecimal subtotal = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Build(billable, lines, subtotal, hours(unapproved), warnings);
    }

    private ProjectRates rates(TimesheetProject p) {
        return new ProjectRates(p.getId(), p.getName(), p.getClientName(), p.isBillable(), p.getBillingRate(), memberRates(p), p.getBudgetMinutes());
    }

    private Map<Long, BigDecimal> memberRates(TimesheetProject p) {
        if (p.getMemberRatesJson() == null) return Map.of();
        try {
            return objectMapper.readValue(p.getMemberRatesJson(), new TypeReference<Map<Long, BigDecimal>>() { });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private InvoiceDTO toDto(ProjectInvoice i, TimesheetProject p) {
        List<Line> lines;
        try {
            lines = objectMapper.readValue(i.getLinesJson(), new TypeReference<List<Line>>() { });
        } catch (Exception ex) {
            lines = List.of();
        }
        return new InvoiceDTO(i.getId(), i.getInvoiceNumber(), i.getProjectId(), p == null ? null : p.getName(), p == null ? null : p.getClientName(),
                i.getPeriodFrom(), i.getPeriodTo(), i.getStatus(), lines, i.getSubtotal(), i.getTaxPercent(), i.getTaxAmount(), i.getTotal(), i.getNotes(),
                i.getCreatedByName(), i.getCreatedAt(), i.getSentAt(), i.getPaidAt());
    }

    private static BigDecimal hours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    private TimesheetProject project(Long id) {
        if (id == null) throw new BadRequestException("Choose a project");
        return projects.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private String write(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void requireManager() {
        if (!ctx.hasOrgScope("TIMESHEET_MANAGE")) throw new AccessDeniedException("Only timesheet admins can manage billing");
    }
}
