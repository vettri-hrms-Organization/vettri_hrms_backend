package com.haodaone.integration;

import com.haodaone.auth.service.MfaSecretCipher;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.entity.LeaveRequest;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.performance.suite.OneOnOne;
import com.haodaone.performance.suite.OneOnOneRepository;
import com.haodaone.performance.suite.PerfContext;
import com.haodaone.travel.TravelRequest;
import com.haodaone.travel.TravelRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Personal calendar subscription (ICS) for Google Calendar / Outlook / Apple Calendar: company holidays, my leave,
 * my direct reports' approved leave, my 1:1s and approved business trips. The link carries a random secret token -
 * anyone with it can read these entries, so it can be reset at any time (the old link stops working).
 */
@Service
public class CalendarFeedService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CalendarFeedRepository feeds;
    private final EmployeeRepository employees;
    private final HolidayRepository holidays;
    private final LeaveRequestRepository leaveRequests;
    private final OneOnOneRepository meetings;
    private final TravelRequestRepository travel;
    private final MfaSecretCipher cipher;
    private final PerfContext ctx;
    private final Clock clock;

    public CalendarFeedService(CalendarFeedRepository feeds, EmployeeRepository employees, HolidayRepository holidays, LeaveRequestRepository leaveRequests,
                               OneOnOneRepository meetings, TravelRequestRepository travel, MfaSecretCipher cipher, PerfContext ctx, Clock applicationClock) {
        this.feeds = feeds;
        this.employees = employees;
        this.holidays = holidays;
        this.leaveRequests = leaveRequests;
        this.meetings = meetings;
        this.travel = travel;
        this.cipher = cipher;
        this.ctx = ctx;
        this.clock = applicationClock;
    }

    public record Link(String path, LocalDateTime lastFetchedAt) { }

    @Transactional(readOnly = true)
    public Link myLink() {
        Employee me = ctx.me();
        return feeds.findFirstByEmployeeIdAndDeletedFalse(me.getId())
                .map(f -> new Link(path(new String(cipher.decrypt(f.getTokenEnc()), StandardCharsets.UTF_8)), f.getLastFetchedAt())).orElse(null);
    }

    /** Creates the link, or replaces it (the old one stops working). */
    @Transactional
    public Link reset() {
        Employee me = ctx.me();
        CalendarFeed f = feeds.findFirstByEmployeeIdAndDeletedFalse(me.getId()).orElseGet(CalendarFeed::new);
        String token = IntegrationService.token(24);
        f.setCompanyId(ctx.companyId());
        f.setEmployeeId(me.getId());
        f.setTokenHash(IntegrationService.sha256(token));
        f.setTokenEnc(cipher.encrypt(token.getBytes(StandardCharsets.UTF_8)));
        f.setLastFetchedAt(null);
        feeds.save(f);
        return new Link(path(token), null);
    }

    @Transactional
    public void remove() {
        Employee me = ctx.me();
        feeds.findFirstByEmployeeIdAndDeletedFalse(me.getId()).ifPresent(f -> { f.setDeleted(true); feeds.save(f); });
    }

    /** The ICS body for a token, or null when the token is unknown / the employee has left the company. */
    @Transactional
    public String render(String token) {
        if (token == null || token.length() < 20) return null;
        CalendarFeed f = feeds.findByTokenHashAndDeletedFalse(IntegrationService.sha256(token)).orElse(null);
        if (f == null) return null;
        Employee me = employees.findById(f.getEmployeeId()).filter(e -> !e.isDeleted()).orElse(null);
        if (me == null || me.getCompany() == null || !me.getCompany().getId().equals(f.getCompanyId())) return null;
        f.setLastFetchedAt(LocalDateTime.now(clock));
        feeds.save(f);

        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(90), to = today.plusDays(365);
        Long companyId = f.getCompanyId();
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//Vettri HRMS//Calendar feed//EN");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "X-WR-CALNAME:Vettri HRMS - " + text(me.getFullName()));
        line(ics, "X-WR-TIMEZONE:Asia/Kolkata");
        line(ics, "REFRESH-INTERVAL;VALUE=DURATION:PT6H");
        String stamp = UTC.format(ZonedDateTime.now(clock).withZoneSameInstant(ZoneOffset.UTC));

        holidays.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, from, to).forEach(h ->
                allDay(ics, "holiday-" + h.getId(), stamp, h.getDate(), h.getDate(), "Holiday: " + h.getName(), null, "TRANSPARENT"));

        for (LeaveRequest l : leaveRequests.findAllByEmployee_Company_IdAndEmployeeIdOrderByStartDateDesc(companyId, me.getId())) {
            if (l.getEndDate().isBefore(from) || l.getStartDate().isAfter(to)) continue;
            if (!"APPROVED".equals(l.getStatus()) && !"PENDING".equals(l.getStatus())) continue;
            String title = l.getLeaveType().getName() + (l.isHalfDay() ? " (half day)" : "") + ("PENDING".equals(l.getStatus()) ? " - pending approval" : "");
            allDay(ics, "leave-" + l.getId(), stamp, l.getStartDate(), l.getEndDate(), title, l.getReason(), "OPAQUE");
        }

        List<Long> reports = employees.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId).stream()
                .filter(e -> e.getReportingManager() != null && me.getId().equals(e.getReportingManager().getId())).map(Employee::getId).toList();
        if (!reports.isEmpty()) {
            leaveRequests.findAllByCompany_IdAndEmployeeIdInAndStatusOrderByStartDateAsc(companyId, reports, "APPROVED").stream()
                    .filter(l -> !l.getEndDate().isBefore(from) && !l.getStartDate().isAfter(to))
                    .forEach(l -> allDay(ics, "team-leave-" + l.getId(), stamp, l.getStartDate(), l.getEndDate(),
                            l.getEmployee().getFullName() + " on leave", l.getLeaveType().getName(), "TRANSPARENT"));
        }

        List<OneOnOne> ones = new ArrayList<>(meetings.findAllByCompanyIdAndManagerEmployeeIdAndDeletedFalseOrderByScheduledAtDesc(companyId, me.getId()));
        ones.addAll(meetings.findAllByCompanyIdAndEmployeeIdAndDeletedFalseOrderByScheduledAtDesc(companyId, me.getId()));
        Map<Long, Employee> people = new HashMap<>();
        for (OneOnOne m : ones) {
            if (!"SCHEDULED".equals(m.getStatus()) || m.getScheduledAt().toLocalDate().isBefore(from)) continue;
            Long otherId = me.getId().equals(m.getManagerEmployeeId()) ? m.getEmployeeId() : m.getManagerEmployeeId();
            Employee other = people.computeIfAbsent(otherId, id -> employees.findById(id).orElse(null));
            ZonedDateTime start = m.getScheduledAt().atZone(IST).withZoneSameInstant(ZoneOffset.UTC);
            line(ics, "BEGIN:VEVENT");
            line(ics, "UID:one-on-one-" + m.getId() + "@vettrihrms");
            line(ics, "DTSTAMP:" + stamp);
            line(ics, "DTSTART:" + UTC.format(start));
            line(ics, "DTEND:" + UTC.format(start.plusMinutes(30)));
            line(ics, "SUMMARY:" + text("1:1 with " + (other == null ? "colleague" : other.getFullName())));
            if (m.getAgenda() != null) line(ics, "DESCRIPTION:" + text(m.getAgenda()));
            line(ics, "END:VEVENT");
        }

        for (TravelRequest t : travel.findAllByCompanyIdAndEmployeeIdAndDeletedFalseOrderByDepartDateDesc(companyId, me.getId())) {
            if (!TravelRequest.APPROVED.equals(t.getStatus()) || t.getReturnDate().isBefore(from)) continue;
            allDay(ics, "trip-" + t.getId(), stamp, t.getDepartDate(), t.getReturnDate(), "Trip: " + t.getFromCity() + " to " + t.getToCity(), t.getPurpose(), "OPAQUE");
        }
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    private static void allDay(StringBuilder ics, String uid, String stamp, LocalDate start, LocalDate end, String summary, String description, String transp) {
        line(ics, "BEGIN:VEVENT");
        line(ics, "UID:" + uid + "@vettrihrms");
        line(ics, "DTSTAMP:" + stamp);
        line(ics, "DTSTART;VALUE=DATE:" + DAY.format(start));
        line(ics, "DTEND;VALUE=DATE:" + DAY.format(end.plusDays(1)));
        line(ics, "SUMMARY:" + text(summary));
        if (description != null && !description.isBlank()) line(ics, "DESCRIPTION:" + text(description));
        line(ics, "TRANSP:" + transp);
        line(ics, "END:VEVENT");
    }

    /** RFC 5545 text escaping. */
    static String text(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "");
    }

    /** Appends a content line, folded at 75 octets as RFC 5545 requires. */
    static void line(StringBuilder ics, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 75) {
            ics.append(content).append("\r\n");
            return;
        }
        int count = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            int len = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (count + len > 75) {
                ics.append(current).append("\r\n ");
                current.setLength(0);
                count = 1;
            }
            current.appendCodePoint(cp);
            count += len;
            i += Character.charCount(cp);
        }
        ics.append(current).append("\r\n");
    }

    private static String path(String token) {
        return "/api/calendar/feed/" + token + ".ics";
    }
}
