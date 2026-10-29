package com.haodaone.supportaccess;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business view of every customer company for Vettri platform staff: plan, status, validity,
 * payments and usage COUNTS. Deliberately contains no employee names, salaries, attendance or
 * activity - those stay inside the company unless it grants support access.
 */
@Service
public class PlatformOverviewService {

    private final JdbcTemplate jdbc;
    private final SupportAccessService supportAccess;
    private final Clock clock;

    public PlatformOverviewService(JdbcTemplate jdbc, SupportAccessService supportAccess, Clock applicationClock) {
        this.jdbc = jdbc;
        this.supportAccess = supportAccess;
        this.clock = applicationClock;
    }

    public record CompanyRow(Long companyId, String name, boolean demo, LocalDateTime signedUpAt,
                             String adminName, String adminEmail,
                             String plan, String billingCycle, String status, LocalDate validUntil, Long daysLeft,
                             long employees, Integer employeeLimit, long devices,
                             BigDecimal totalPaid, BigDecimal lastPaymentAmount, LocalDateTime lastPaymentAt,
                             LocalDateTime lastActiveAt, long activeUsers7d,
                             String supportAccessLevel, LocalDateTime supportAccessUntil) { }

    public record Summary(long companies, long paying, long trials, long trialsEndingThisWeek, long overdue,
                          long readOnly, long employees, long devices, BigDecimal revenueThisMonth, BigDecimal revenueTotal) { }

    public record Overview(Summary summary, List<CompanyRow> companies) { }

    @Transactional(readOnly = true)
    public Overview overview() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime weekAgo = LocalDateTime.now(clock).minusDays(7);
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        Map<Long, Long> employees = counts("""
                SELECT company_id, COUNT(*) FROM employee
                WHERE deleted = FALSE AND (employment_status IS NULL OR UPPER(employment_status) NOT IN ('RESIGNED', 'TERMINATED'))
                GROUP BY company_id""");
        Map<Long, Long> devices = counts("SELECT company_id, COUNT(*) FROM monitored_device WHERE deleted = FALSE GROUP BY company_id");

        Map<Long, BigDecimal> paid = new HashMap<>();
        Map<Long, Object[]> lastPayment = new HashMap<>();
        BigDecimal[] monthRevenue = {BigDecimal.ZERO};
        jdbc.query("""
                SELECT company_id, total, paid_at FROM billing_invoice
                WHERE status = 'PAID' AND invoice_type <> 'CREDIT_NOTE' AND deleted = FALSE
                ORDER BY paid_at ASC""", rs -> {
            long companyId = rs.getLong(1);
            BigDecimal total = rs.getBigDecimal(2) == null ? BigDecimal.ZERO : rs.getBigDecimal(2);
            Timestamp paidAt = rs.getTimestamp(3);
            paid.merge(companyId, total, BigDecimal::add);
            lastPayment.put(companyId, new Object[]{total, paidAt == null ? null : paidAt.toLocalDateTime()});
            if (paidAt != null && !paidAt.toLocalDateTime().isBefore(monthStart)) monthRevenue[0] = monthRevenue[0].add(total);
        });

        Map<Long, Object[]> activity = new HashMap<>();
        jdbc.query("""
                SELECT company_id, MAX(last_login_at), SUM(CASE WHEN last_login_at >= ? THEN 1 ELSE 0 END)
                FROM app_user WHERE deleted = FALSE AND company_id IS NOT NULL GROUP BY company_id""",
                rs -> {
                    Timestamp last = rs.getTimestamp(2);
                    activity.put(rs.getLong(1), new Object[]{last == null ? null : last.toLocalDateTime(), rs.getLong(3)});
                }, Timestamp.valueOf(weekAgo));

        Map<Long, String[]> admins = new HashMap<>();
        jdbc.query("""
                SELECT u.company_id, u.full_name, u.email FROM app_user u
                JOIN user_roles ur ON ur.user_id = u.id JOIN role r ON r.id = ur.role_id
                WHERE r.name = 'COMPANY_ADMIN' AND u.deleted = FALSE AND u.company_id IS NOT NULL
                ORDER BY u.id ASC""", rs -> {
            admins.putIfAbsent(rs.getLong(1), new String[]{rs.getString(2), rs.getString(3)});
        });

        Map<Long, SupportAccessService.AccessibleCompany> support = new HashMap<>();
        supportAccess.accessibleCompanies().forEach(item -> support.put(item.companyId(), item));

        List<CompanyRow> rows = new ArrayList<>();
        jdbc.query("""
                SELECT c.id, c.name, c.is_demo, c.created_at, s.plan, s.billing_cycle, s.status, s.renewal_date, s.employee_limit
                FROM company c LEFT JOIN subscription s ON s.company_id = c.id AND s.deleted = FALSE
                WHERE c.deleted = FALSE ORDER BY c.created_at DESC""", rs -> {
            long id = rs.getLong(1);
            java.sql.Date renewal = rs.getDate(8);
            LocalDate validUntil = renewal == null ? null : renewal.toLocalDate();
            Object[] last = lastPayment.get(id);
            Object[] act = activity.getOrDefault(id, new Object[]{null, 0L});
            String[] admin = admins.get(id);
            SupportAccessService.AccessibleCompany access = support.get(id);
            boolean demo = rs.getBoolean(3);
            Timestamp created = rs.getTimestamp(4);
            Number limit = (Number) rs.getObject(9);
            rows.add(new CompanyRow(id, rs.getString(2), demo, created == null ? null : created.toLocalDateTime(),
                    admin == null ? null : admin[0], admin == null ? null : admin[1],
                    rs.getString(5), rs.getString(6), rs.getString(7), validUntil,
                    validUntil == null ? null : ChronoUnit.DAYS.between(today, validUntil),
                    employees.getOrDefault(id, 0L), limit == null ? null : limit.intValue(), devices.getOrDefault(id, 0L),
                    paid.getOrDefault(id, BigDecimal.ZERO),
                    last == null ? null : (BigDecimal) last[0], last == null ? null : (LocalDateTime) last[1],
                    (LocalDateTime) act[0], ((Number) act[1]).longValue(),
                    access == null || demo ? null : access.accessLevel(), access == null || demo ? null : access.expiresAt()));
        });

        long paying = 0, trials = 0, ending = 0, overdue = 0, readOnly = 0, staff = 0, laptops = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (CompanyRow row : rows) {
            if (row.demo()) continue;
            String status = row.status() == null ? "" : row.status();
            boolean trial = status.equals("TRIALING") || status.equals("TRIAL") || "TRIAL".equals(row.plan());
            if (status.equals("ACTIVE") && !trial) paying++;
            if (trial && !status.equals("EXPIRED")) {
                trials++;
                if (row.daysLeft() != null && row.daysLeft() >= 0 && row.daysLeft() <= 7) ending++;
            }
            if (status.equals("PAST_DUE")) overdue++;
            if (status.equals("SUSPENDED") || status.equals("EXPIRED") || status.equals("CANCELLED")) readOnly++;
            staff += row.employees();
            laptops += row.devices();
            total = total.add(row.totalPaid());
        }
        long customers = rows.stream().filter(row -> !row.demo()).count();
        return new Overview(new Summary(customers, paying, trials, ending, overdue, readOnly, staff, laptops, monthRevenue[0], total), rows);
    }

    private Map<Long, Long> counts(String sql) {
        Map<Long, Long> result = new HashMap<>();
        jdbc.query(sql, rs -> { result.put(rs.getLong(1), rs.getLong(2)); });
        return result;
    }
}
