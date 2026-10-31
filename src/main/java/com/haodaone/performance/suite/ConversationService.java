package com.haodaone.performance.suite;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

/**
 * 1:1 meetings and continuous feedback. A manager (or HR) schedules a 1:1 with a report; both can add to the agenda and
 * shared notes, the manager also keeps private notes the employee never sees. Anyone can give feedback to a colleague:
 * PUBLIC praise appears on the company kudos wall, PRIVATE feedback is seen by the recipient, the sender, the recipient's
 * manager and HR.
 */
@Service
public class ConversationService {

    public static final Set<String> FEEDBACK_KINDS = Set.of("PRAISE", "CONSTRUCTIVE", "REQUEST");

    private final OneOnOneRepository meetings;
    private final FeedbackNoteRepository feedback;
    private final PerfContext ctx;


    private com.haodaone.integration.IntegrationEventService integrationEvents;

    /** Slack/Teams posts and outbound webhooks (optional so unit tests can omit it). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setIntegrationEvents(com.haodaone.integration.IntegrationEventService integrationEvents) {
        this.integrationEvents = integrationEvents;
    }

    public ConversationService(OneOnOneRepository meetings, FeedbackNoteRepository feedback, PerfContext ctx) {
        this.meetings = meetings;
        this.feedback = feedback;
        this.ctx = ctx;
    }

    public record MeetingRequest(Long employeeId, LocalDateTime scheduledAt, String agenda) { }
    public record MeetingUpdate(LocalDateTime scheduledAt, String agenda, String sharedNotes, String privateNotes, String actionItems) { }
    public record MeetingDTO(Long id, Long managerEmployeeId, String managerName, Long employeeId, String employeeName, LocalDateTime scheduledAt,
                             String status, String agenda, String sharedNotes, String privateNotes, String actionItems, boolean iAmManager) { }
    public record FeedbackRequest(Long toEmployeeId, String kind, String visibility, String valueTag, String message) { }
    public record FeedbackDTO(Long id, Long fromEmployeeId, String fromName, Long toEmployeeId, String toName, String kind, String visibility,
                              String valueTag, String message, LocalDateTime createdAt) { }

    // ------------------------------------------------------------------ 1:1s

    @Transactional(readOnly = true)
    public List<MeetingDTO> myMeetings() {
        Employee me = ctx.me();
        List<OneOnOne> rows = Stream.concat(
                meetings.findAllByCompanyIdAndManagerEmployeeIdAndDeletedFalseOrderByScheduledAtDesc(ctx.companyId(), me.getId()).stream(),
                meetings.findAllByCompanyIdAndEmployeeIdAndDeletedFalseOrderByScheduledAtDesc(ctx.companyId(), me.getId()).stream())
                .sorted(Comparator.comparing(OneOnOne::getScheduledAt).reversed()).toList();
        Map<Long, Employee> people = ctx.people(rows.stream().flatMap(m -> Stream.of(m.getManagerEmployeeId(), m.getEmployeeId())).toList());
        return rows.stream().map(m -> toDto(m, me, people)).toList();
    }

    @Transactional
    public MeetingDTO schedule(MeetingRequest r) {
        Employee me = ctx.me();
        if (r == null || r.employeeId() == null || r.scheduledAt() == null) throw new BadRequestException("Choose who and when");
        Employee employee = ctx.employee(r.employeeId());
        if (employee.getId().equals(me.getId())) throw new BadRequestException("Pick someone other than yourself");
        if (!ctx.manages(me.getId(), employee) && !ctx.isHr()) throw new AccessDeniedException("You can schedule 1:1s with your direct reports");
        OneOnOne m = new OneOnOne();
        m.setCompanyId(ctx.companyId());
        m.setManagerEmployeeId(me.getId());
        m.setEmployeeId(employee.getId());
        m.setScheduledAt(r.scheduledAt());
        m.setStatus("SCHEDULED");
        m.setAgenda(PerfContext.text(r.agenda(), 5000));
        m = meetings.save(m);
        ctx.notify(employee, "PERFORMANCE", "1:1 scheduled", me.getFullName() + " set up a 1:1 on " + r.scheduledAt().toLocalDate() + ". Add anything you'd like to discuss.", "ONE_ON_ONE", m.getId());
        return toDto(m, me, ctx.people(List.of(me.getId(), employee.getId())));
    }

    @Transactional
    public MeetingDTO update(Long id, MeetingUpdate u) {
        Employee me = ctx.me();
        OneOnOne m = meeting(id);
        boolean manager = me.getId().equals(m.getManagerEmployeeId());
        if (!manager && !me.getId().equals(m.getEmployeeId())) throw new AccessDeniedException("Not your 1:1");
        if (u == null) throw new BadRequestException("Nothing to save");
        if (u.agenda() != null) m.setAgenda(PerfContext.text(u.agenda(), 5000));
        if (u.sharedNotes() != null) m.setSharedNotes(PerfContext.text(u.sharedNotes(), 10000));
        if (u.actionItems() != null) m.setActionItems(PerfContext.text(u.actionItems(), 5000));
        if (manager) {
            if (u.privateNotes() != null) m.setPrivateNotes(PerfContext.text(u.privateNotes(), 10000));
            if (u.scheduledAt() != null) m.setScheduledAt(u.scheduledAt());
        }
        meetings.save(m);
        return toDto(m, me, ctx.people(List.of(m.getManagerEmployeeId(), m.getEmployeeId())));
    }

    @Transactional
    public MeetingDTO setStatus(Long id, String status) {
        Employee me = ctx.me();
        OneOnOne m = meeting(id);
        if (!me.getId().equals(m.getManagerEmployeeId())) throw new AccessDeniedException("Only the manager can do this");
        String s = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (!Set.of("SCHEDULED", "COMPLETED", "CANCELLED").contains(s)) throw new BadRequestException("Unknown status");
        m.setStatus(s);
        meetings.save(m);
        Map<Long, Employee> people = ctx.people(List.of(m.getManagerEmployeeId(), m.getEmployeeId()));
        if ("COMPLETED".equals(s) && m.getActionItems() != null)
            ctx.notify(people.get(m.getEmployeeId()), "PERFORMANCE", "1:1 notes are ready", "Action items from your 1:1 with " + me.getFullName() + " are in Conversations.", "ONE_ON_ONE", m.getId());
        return toDto(m, me, people);
    }

    // ------------------------------------------------------------------ feedback

    @Transactional
    public FeedbackDTO give(FeedbackRequest r) {
        Employee me = ctx.me();
        if (r == null || r.toEmployeeId() == null) throw new BadRequestException("Choose who the feedback is for");
        Employee to = ctx.employee(r.toEmployeeId());
        if (to.getId().equals(me.getId())) throw new BadRequestException("You can't send feedback to yourself");
        String kind = r.kind() == null ? "PRAISE" : r.kind().toUpperCase(Locale.ROOT);
        if (!FEEDBACK_KINDS.contains(kind)) throw new BadRequestException("Unknown feedback type");
        String visibility = r.visibility() == null ? "PRIVATE" : r.visibility().toUpperCase(Locale.ROOT);
        if (!Set.of("PUBLIC", "PRIVATE").contains(visibility)) throw new BadRequestException("Unknown visibility");
        if (!"PRAISE".equals(kind)) visibility = "PRIVATE";
        String message = PerfContext.text(r.message(), 2000);
        if (message == null) throw new BadRequestException("Write a message");
        FeedbackNote f = new FeedbackNote();
        f.setCompanyId(ctx.companyId());
        f.setFromEmployeeId(me.getId());
        f.setToEmployeeId(to.getId());
        f.setKind(kind);
        f.setVisibility(visibility);
        f.setValueTag(PerfContext.text(r.valueTag(), 40));
        f.setMessage(message);
        f = feedback.save(f);
        String title = switch (kind) {
            case "PRAISE" -> me.getFullName() + " praised you";
            case "REQUEST" -> me.getFullName() + " asked for your feedback";
            default -> me.getFullName() + " shared feedback";
        };
        if ("PUBLIC".equals(visibility) && integrationEvents != null) {
            integrationEvents.publish(ctx.companyId(), com.haodaone.integration.IntegrationEventService.KUDOS_POSTED,
                    "Kudos from " + me.getFullName() + " to " + to.getFullName() + (f.getValueTag() != null ? " #" + f.getValueTag() : "") + ": " + message,
                    java.util.Map.of("feedbackId", f.getId(), "from", me.getFullName(), "to", to.getFullName(), "message", message));
        }
        ctx.notify(to, "PERFORMANCE", title, message.length() > 140 ? message.substring(0, 137) + "..." : message, "FEEDBACK", f.getId());
        return toDto(f, ctx.people(List.of(me.getId(), to.getId())));
    }

    @Transactional(readOnly = true)
    public List<FeedbackDTO> wall() {
        List<FeedbackNote> rows = feedback.findTop100ByCompanyIdAndVisibilityAndDeletedFalseOrderByCreatedAtDesc(ctx.companyId(), "PUBLIC");
        return toDtos(rows);
    }

    @Transactional(readOnly = true)
    public List<FeedbackDTO> received(Long employeeId) {
        Employee me = ctx.me();
        Long target = employeeId == null ? me.getId() : employeeId;
        if (!target.equals(me.getId()) && !ctx.isHr() && !ctx.manages(me.getId(), ctx.employee(target)))
            throw new AccessDeniedException("You can see feedback for yourself and your team");
        return toDtos(feedback.findAllByCompanyIdAndToEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(ctx.companyId(), target));
    }

    @Transactional(readOnly = true)
    public List<FeedbackDTO> given() {
        return toDtos(feedback.findAllByCompanyIdAndFromEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(ctx.companyId(), ctx.me().getId()));
    }

    // ------------------------------------------------------------------ helpers

    private OneOnOne meeting(Long id) {
        return meetings.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("1:1 not found"));
    }

    private MeetingDTO toDto(OneOnOne m, Employee me, Map<Long, Employee> people) {
        boolean manager = me.getId().equals(m.getManagerEmployeeId());
        return new MeetingDTO(m.getId(), m.getManagerEmployeeId(), PerfContext.name(people, m.getManagerEmployeeId()), m.getEmployeeId(),
                PerfContext.name(people, m.getEmployeeId()), m.getScheduledAt(), m.getStatus(), m.getAgenda(), m.getSharedNotes(),
                manager ? m.getPrivateNotes() : null, m.getActionItems(), manager);
    }

    private List<FeedbackDTO> toDtos(List<FeedbackNote> rows) {
        Map<Long, Employee> people = ctx.people(rows.stream().flatMap(f -> Stream.of(f.getFromEmployeeId(), f.getToEmployeeId())).toList());
        return rows.stream().map(f -> toDto(f, people)).toList();
    }

    private static FeedbackDTO toDto(FeedbackNote f, Map<Long, Employee> people) {
        return new FeedbackDTO(f.getId(), f.getFromEmployeeId(), PerfContext.name(people, f.getFromEmployeeId()), f.getToEmployeeId(),
                PerfContext.name(people, f.getToEmployeeId()), f.getKind(), f.getVisibility(), f.getValueTag(), f.getMessage(), f.getCreatedAt());
    }
}
