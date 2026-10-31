package com.haodaone.helpdesk;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.performance.suite.PerfContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

/**
 * HR helpdesk. Employees raise tickets by category (payroll, leave, IT access, documents...). HR agents (EMPLOYEE_MANAGE
 * at organization scope) and the category's owner work them: reply, add internal notes, reassign, set status. Each
 * category has a resolution SLA; overdue tickets are flagged. Confidential tickets are visible only to the employee,
 * the assignee and HR. Resolved tickets close automatically after 7 days, and the employee can rate the resolution.
 */
@Service
public class HelpdeskService {

    public static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    static final List<String[]> DEFAULT_CATEGORIES = List.of(
            new String[]{"Payroll & salary", "Payslips, salary credit, deductions, tax", "48"},
            new String[]{"Leave & attendance", "Balances, regularisation, holidays", "24"},
            new String[]{"Documents & letters", "Employment, address proof and other letters", "72"},
            new String[]{"Benefits & insurance", "Insurance, reimbursements, FBP", "72"},
            new String[]{"IT & access", "Laptop, accounts and access requests", "24"},
            new String[]{"Policy question", "Anything about company policies", "72"},
            new String[]{"Grievance", "Raise a concern confidentially", "48"});

    private final HelpdeskCategoryRepository categories;
    private final HelpdeskTicketRepository tickets;
    private final HelpdeskCommentRepository comments;
    private final PerfContext ctx;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    private com.haodaone.integration.IntegrationEventService integrationEvents;

    /** Slack/Teams posts and outbound webhooks (optional so unit tests can omit it). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setIntegrationEvents(com.haodaone.integration.IntegrationEventService integrationEvents) {
        this.integrationEvents = integrationEvents;
    }

    public HelpdeskService(HelpdeskCategoryRepository categories, HelpdeskTicketRepository tickets, HelpdeskCommentRepository comments,
                           PerfContext ctx, NotificationService notificationService, AuditLogService auditLogService, Clock applicationClock) {
        this.categories = categories;
        this.tickets = tickets;
        this.comments = comments;
        this.ctx = ctx;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.clock = applicationClock;
    }

    public record CategoryDTO(Long id, String name, String description, Long assigneeEmployeeId, String assigneeName, int slaHours, boolean active) { }
    public record CategoryRequest(String name, String description, Long assigneeEmployeeId, Integer slaHours, Boolean active) { }
    public record TicketRequest(Long categoryId, String subject, String description, String priority, Boolean confidential) { }
    public record CommentRequest(String body, Boolean internal, String status) { }
    public record UpdateRequest(String status, String priority, Long assigneeEmployeeId, Long categoryId) { }
    public record RatingRequest(Integer rating, String comment) { }
    public record CommentDTO(Long id, String authorName, boolean fromEmployee, String body, boolean internal, LocalDateTime at) { }
    public record TicketDTO(Long id, String ticketNumber, Long employeeId, String employeeName, String employeeCode, Long categoryId, String categoryName,
                            String subject, String description, String priority, String status, Long assigneeEmployeeId, String assigneeName,
                            LocalDateTime createdAt, LocalDateTime dueAt, boolean overdue, LocalDateTime firstResponseAt, LocalDateTime resolvedAt,
                            Integer rating, String ratingComment, boolean confidential, boolean canWork, List<CommentDTO> comments) { }
    public record Summary(long open, long overdue, long unassigned, long resolvedThisWeek, Double averageRating) { }

    // ------------------------------------------------------------------ categories

    @Transactional
    public List<CategoryDTO> categories() {
        Long companyId = ctx.companyId();
        List<HelpdeskCategory> rows = categories.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(companyId);
        if (rows.isEmpty()) {
            for (String[] d : DEFAULT_CATEGORIES) {
                HelpdeskCategory c = new HelpdeskCategory();
                c.setCompanyId(companyId);
                c.setName(d[0]);
                c.setDescription(d[1]);
                c.setSlaHours(Integer.parseInt(d[2]));
                c.setActive(true);
                categories.save(c);
            }
            rows = categories.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(companyId);
        }
        Map<Long, Employee> people = ctx.people(rows.stream().map(HelpdeskCategory::getAssigneeEmployeeId).toList());
        return rows.stream().map(c -> new CategoryDTO(c.getId(), c.getName(), c.getDescription(), c.getAssigneeEmployeeId(),
                PerfContext.name(people, c.getAssigneeEmployeeId()), c.getSlaHours(), c.isActive())).toList();
    }

    @Transactional
    public List<CategoryDTO> saveCategory(Long id, CategoryRequest r) {
        requireAgent();
        HelpdeskCategory c = id == null ? new HelpdeskCategory()
                : categories.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        String name = PerfContext.text(r == null ? null : r.name(), 80);
        if (name == null) throw new BadRequestException("Name the category");
        int sla = r.slaHours() == null ? 48 : r.slaHours();
        if (sla < 1 || sla > 720) throw new BadRequestException("SLA must be 1-720 hours");
        if (r.assigneeEmployeeId() != null) ctx.employee(r.assigneeEmployeeId());
        c.setCompanyId(ctx.companyId());
        c.setName(name);
        c.setDescription(PerfContext.text(r.description(), 300));
        c.setAssigneeEmployeeId(r.assigneeEmployeeId());
        c.setSlaHours(sla);
        c.setActive(!Boolean.FALSE.equals(r.active()));
        categories.save(c);
        return categories();
    }

    // ------------------------------------------------------------------ tickets

    @Transactional
    public TicketDTO raise(TicketRequest r) {
        Employee me = ctx.me();
        if (r == null || r.categoryId() == null) throw new BadRequestException("Choose a category");
        HelpdeskCategory c = categories.findByIdAndCompanyIdAndDeletedFalse(r.categoryId(), ctx.companyId())
                .filter(HelpdeskCategory::isActive).orElseThrow(() -> new BadRequestException("Choose a category"));
        String subject = PerfContext.text(r.subject(), 200);
        String description = PerfContext.text(r.description(), 10000);
        if (subject == null || description == null) throw new BadRequestException("Add a subject and describe the issue");
        String priority = r.priority() == null ? "NORMAL" : r.priority().toUpperCase(Locale.ROOT);
        if (!PRIORITIES.contains(priority)) throw new BadRequestException("Unknown priority");
        HelpdeskTicket t = new HelpdeskTicket();
        t.setCompanyId(ctx.companyId());
        t.setTicketNumber("HD-" + String.format("%05d", tickets.countByCompanyId(ctx.companyId()) + 1));
        t.setEmployeeId(me.getId());
        t.setCategoryId(c.getId());
        t.setSubject(subject);
        t.setDescription(description);
        t.setPriority(priority);
        t.setStatus(HelpdeskTicket.OPEN);
        t.setConfidential(Boolean.TRUE.equals(r.confidential()) || "Grievance".equalsIgnoreCase(c.getName()));
        t.setAssigneeEmployeeId(c.getAssigneeEmployeeId() != null && !c.getAssigneeEmployeeId().equals(me.getId()) ? c.getAssigneeEmployeeId() : null);
        int hours = "URGENT".equals(priority) ? Math.min(c.getSlaHours(), 8) : "HIGH".equals(priority) ? Math.max(1, c.getSlaHours() / 2) : c.getSlaHours();
        t.setDueAt(LocalDateTime.now(clock).plusHours(hours));
        t = tickets.save(t);
        String msg = me.getFullName() + " raised " + t.getTicketNumber() + ": " + subject;
        if (integrationEvents != null) {
            // Webhooks only carry identifiers for confidential tickets.
            integrationEvents.publish(ctx.companyId(), com.haodaone.integration.IntegrationEventService.HELPDESK_TICKET_RAISED, "New helpdesk ticket " + t.getTicketNumber(),
                    t.isConfidential() ? java.util.Map.of("ticketId", t.getId(), "ticketNumber", t.getTicketNumber(), "confidential", true)
                            : java.util.Map.of("ticketId", t.getId(), "ticketNumber", t.getTicketNumber(), "category", c.getName(), "subject", subject, "priority", priority));
        }
        if (t.getAssigneeEmployeeId() != null) {
            ctx.notify(ctx.people(List.of(t.getAssigneeEmployeeId())).get(t.getAssigneeEmployeeId()), "HELPDESK", "New ticket assigned to you", msg, "HELPDESK_TICKET", t.getId());
        } else {
            notificationService.notifyPermissionRecipients(ctx.companyId(), me.getId(), "EMPLOYEE_MANAGE", "HELPDESK", "New helpdesk ticket", msg, "HELPDESK_TICKET", t.getId());
        }
        return toDto(t, true);
    }

    @Transactional(readOnly = true)
    public List<TicketDTO> mine() {
        Employee me = ctx.me();
        return tickets.findAllByCompanyIdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(ctx.companyId(), me.getId()).stream().map(t -> toDto(t, false)).toList();
    }

    /** Agent queue: everything for HR; category owners see tickets assigned to them. */
    @Transactional(readOnly = true)
    public List<TicketDTO> queue(String status) {
        Employee me = ctx.meOrNull();
        boolean hr = isAgent();
        if (!hr && me == null) throw new AccessDeniedException("Not a helpdesk agent");
        List<TicketDTO> out = tickets.findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(ctx.companyId()).stream()
                .filter(t -> hr || me.getId().equals(t.getAssigneeEmployeeId()))
                .filter(t -> status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                        || ("ACTIVE".equalsIgnoreCase(status) ? !HelpdeskTicket.RESOLVED.equals(t.getStatus()) && !HelpdeskTicket.CLOSED.equals(t.getStatus())
                        : status.equalsIgnoreCase(t.getStatus())))
                .map(t -> toDto(t, false)).toList();
        if (!hr && out.isEmpty() && categories.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(ctx.companyId()).stream().noneMatch(c -> me.getId().equals(c.getAssigneeEmployeeId())))
            throw new AccessDeniedException("Not a helpdesk agent");
        return out;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        requireAgent();
        List<HelpdeskTicket> all = tickets.findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(ctx.companyId());
        LocalDateTime now = LocalDateTime.now(clock);
        long open = all.stream().filter(this::active).count();
        long overdue = all.stream().filter(t -> active(t) && t.getDueAt() != null && t.getDueAt().isBefore(now)).count();
        long unassigned = all.stream().filter(t -> active(t) && t.getAssigneeEmployeeId() == null).count();
        long resolvedWeek = all.stream().filter(t -> t.getResolvedAt() != null && t.getResolvedAt().isAfter(now.minusDays(7))).count();
        OptionalDouble avg = all.stream().filter(t -> t.getRating() != null).mapToInt(HelpdeskTicket::getRating).average();
        return new Summary(open, overdue, unassigned, resolvedWeek, avg.isPresent() ? Math.round(avg.getAsDouble() * 10) / 10.0 : null);
    }

    @Transactional(readOnly = true)
    public TicketDTO get(Long id) {
        HelpdeskTicket t = visible(id);
        return toDto(t, true);
    }

    @Transactional
    public TicketDTO comment(Long id, CommentRequest r) {
        Employee me = ctx.me();
        HelpdeskTicket t = visible(id);
        boolean agent = canWork(t, me);
        boolean owner = me.getId().equals(t.getEmployeeId());
        String body = PerfContext.text(r == null ? null : r.body(), 10000);
        if (body == null) throw new BadRequestException("Write a reply");
        if (HelpdeskTicket.CLOSED.equals(t.getStatus())) throw new BadRequestException("This ticket is closed - raise a new one");
        boolean internal = agent && !owner && Boolean.TRUE.equals(r.internal());
        HelpdeskComment c = new HelpdeskComment();
        c.setCompanyId(t.getCompanyId());
        c.setTicketId(t.getId());
        c.setAuthorEmployeeId(me.getId());
        c.setAuthorName(me.getFullName());
        c.setBody(body);
        c.setInternal(internal);
        comments.save(c);
        Map<Long, Employee> people = ctx.people(List.of(t.getEmployeeId(), t.getAssigneeEmployeeId() == null ? me.getId() : t.getAssigneeEmployeeId()));
        if (owner) {
            if (HelpdeskTicket.WAITING_ON_EMPLOYEE.equals(t.getStatus()) || HelpdeskTicket.RESOLVED.equals(t.getStatus())) {
                t.setStatus(HelpdeskTicket.IN_PROGRESS);
                t.setResolvedAt(null);
            }
            if (t.getAssigneeEmployeeId() != null) ctx.notify(people.get(t.getAssigneeEmployeeId()), "HELPDESK", t.getTicketNumber() + ": new reply", me.getFullName() + " replied.", "HELPDESK_TICKET", t.getId());
        } else if (!internal) {
            if (t.getFirstResponseAt() == null) t.setFirstResponseAt(LocalDateTime.now(clock));
            if (t.getAssigneeEmployeeId() == null) t.setAssigneeEmployeeId(me.getId());
            applyStatus(t, r.status() == null ? (HelpdeskTicket.OPEN.equals(t.getStatus()) ? HelpdeskTicket.IN_PROGRESS : null) : r.status());
            ctx.notify(people.get(t.getEmployeeId()), "HELPDESK", t.getTicketNumber() + ": HR replied", body.length() > 140 ? body.substring(0, 137) + "..." : body, "HELPDESK_TICKET", t.getId());
        }
        tickets.save(t);
        return toDto(t, true);
    }

    @Transactional
    public TicketDTO update(Long id, UpdateRequest r) {
        Employee me = ctx.me();
        HelpdeskTicket t = visible(id);
        if (!canWork(t, me)) throw new AccessDeniedException("Only HR or the assignee can change this ticket");
        if (r == null) throw new BadRequestException("Nothing to change");
        if (r.priority() != null) {
            String p = r.priority().toUpperCase(Locale.ROOT);
            if (!PRIORITIES.contains(p)) throw new BadRequestException("Unknown priority");
            t.setPriority(p);
        }
        if (r.categoryId() != null) {
            categories.findByIdAndCompanyIdAndDeletedFalse(r.categoryId(), ctx.companyId()).orElseThrow(() -> new BadRequestException("Unknown category"));
            t.setCategoryId(r.categoryId());
        }
        if (r.assigneeEmployeeId() != null && !r.assigneeEmployeeId().equals(t.getAssigneeEmployeeId())) {
            Employee assignee = ctx.employee(r.assigneeEmployeeId());
            if (assignee.getId().equals(t.getEmployeeId())) throw new BadRequestException("A ticket can't be assigned to the person who raised it");
            t.setAssigneeEmployeeId(assignee.getId());
            if (!assignee.getId().equals(me.getId())) ctx.notify(assignee, "HELPDESK", "Ticket assigned to you", t.getTicketNumber() + ": " + t.getSubject(), "HELPDESK_TICKET", t.getId());
        }
        if (r.status() != null) {
            applyStatus(t, r.status());
            if (HelpdeskTicket.RESOLVED.equals(t.getStatus()))
                ctx.notify(ctx.people(List.of(t.getEmployeeId())).get(t.getEmployeeId()), "HELPDESK", t.getTicketNumber() + " resolved",
                        "HR marked your ticket resolved. Reply if it isn't, or rate the help you got.", "HELPDESK_TICKET", t.getId());
        }
        tickets.save(t);
        auditLogService.log("HELPDESK_TICKET", t.getId(), "UPDATED", me.getFullName() + " updated " + t.getTicketNumber() + " (" + t.getStatus() + ")");
        return toDto(t, true);
    }

    @Transactional
    public TicketDTO rate(Long id, RatingRequest r) {
        Employee me = ctx.me();
        HelpdeskTicket t = visible(id);
        if (!me.getId().equals(t.getEmployeeId())) throw new AccessDeniedException("Only the person who raised it can rate it");
        if (!HelpdeskTicket.RESOLVED.equals(t.getStatus()) && !HelpdeskTicket.CLOSED.equals(t.getStatus())) throw new BadRequestException("Rate once it's resolved");
        if (r == null || r.rating() == null || r.rating() < 1 || r.rating() > 5) throw new BadRequestException("Rate from 1 to 5");
        t.setRating(r.rating());
        t.setRatingComment(PerfContext.text(r.comment(), 500));
        t.setStatus(HelpdeskTicket.CLOSED);
        t.setClosedAt(LocalDateTime.now(clock));
        tickets.save(t);
        return toDto(t, true);
    }

    /** Daily: resolved tickets with no reply for 7 days close themselves. */
    @Transactional
    public int autoClose(LocalDateTime now) {
        int n = 0;
        for (HelpdeskTicket t : tickets.findAllByStatusAndResolvedAtBeforeAndDeletedFalse(HelpdeskTicket.RESOLVED, now.minusDays(7))) {
            t.setStatus(HelpdeskTicket.CLOSED);
            t.setClosedAt(now);
            tickets.save(t);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ helpers

    private void applyStatus(HelpdeskTicket t, String status) {
        if (status == null) return;
        String s = status.toUpperCase(Locale.ROOT);
        if (!Set.of(HelpdeskTicket.OPEN, HelpdeskTicket.IN_PROGRESS, HelpdeskTicket.WAITING_ON_EMPLOYEE, HelpdeskTicket.RESOLVED, HelpdeskTicket.CLOSED).contains(s))
            throw new BadRequestException("Unknown status");
        t.setStatus(s);
        if (HelpdeskTicket.RESOLVED.equals(s)) t.setResolvedAt(LocalDateTime.now(clock));
        if (HelpdeskTicket.CLOSED.equals(s)) t.setClosedAt(LocalDateTime.now(clock));
    }

    private boolean active(HelpdeskTicket t) {
        return !HelpdeskTicket.RESOLVED.equals(t.getStatus()) && !HelpdeskTicket.CLOSED.equals(t.getStatus());
    }

    private boolean isAgent() {
        return ctx.hasOrgScope("EMPLOYEE_MANAGE");
    }

    private void requireAgent() {
        if (!isAgent()) throw new AccessDeniedException("Only HR can do this");
    }

    private boolean canWork(HelpdeskTicket t, Employee me) {
        if (me != null && me.getId().equals(t.getEmployeeId())) return false;
        return isAgent() || (me != null && me.getId().equals(t.getAssigneeEmployeeId()));
    }

    private HelpdeskTicket visible(Long id) {
        HelpdeskTicket t = tickets.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        Employee me = ctx.meOrNull();
        boolean owner = me != null && me.getId().equals(t.getEmployeeId());
        if (!owner && !canWork(t, me)) throw new ResourceNotFoundException("Ticket not found");
        return t;
    }

    private TicketDTO toDto(HelpdeskTicket t, boolean withComments) {
        Employee me = ctx.meOrNull();
        boolean agent = canWork(t, me);
        List<HelpdeskComment> thread = withComments ? comments.findAllByTicketIdAndDeletedFalseOrderByCreatedAtAsc(t.getId()) : List.of();
        Map<Long, Employee> people = ctx.people(Stream.of(t.getEmployeeId(), t.getAssigneeEmployeeId()).filter(Objects::nonNull).toList());
        Employee e = people.get(t.getEmployeeId());
        HelpdeskCategory c = t.getCategoryId() == null ? null : categories.findById(t.getCategoryId()).orElse(null);
        List<CommentDTO> visibleThread = thread.stream().filter(x -> agent || !x.isInternal())
                .map(x -> new CommentDTO(x.getId(), x.getAuthorName(), Objects.equals(x.getAuthorEmployeeId(), t.getEmployeeId()), x.getBody(), x.isInternal(), x.getCreatedAt())).toList();
        boolean overdue = active(t) && t.getDueAt() != null && t.getDueAt().isBefore(LocalDateTime.now(clock));
        return new TicketDTO(t.getId(), t.getTicketNumber(), t.getEmployeeId(), e == null ? null : e.getFullName(), e == null ? null : e.getEmployeeCode(),
                t.getCategoryId(), c == null ? null : c.getName(), t.getSubject(), t.getDescription(), t.getPriority(), t.getStatus(),
                t.getAssigneeEmployeeId(), PerfContext.name(people, t.getAssigneeEmployeeId()), t.getCreatedAt(), t.getDueAt(), overdue,
                t.getFirstResponseAt(), t.getResolvedAt(), t.getRating(), t.getRatingComment(), t.isConfidential(), agent, visibleThread);
    }
}
