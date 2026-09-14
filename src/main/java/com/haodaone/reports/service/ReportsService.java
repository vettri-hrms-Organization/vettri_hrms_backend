package com.haodaone.reports.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportsService {

    private final JdbcTemplate jdbc;

    public ReportsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Attendance summary per day in range: total punches, unique employees punched */
    public List<Map<String,Object>> attendanceSummary(Long companyId, LocalDate from, LocalDate to) {
        String sql = "SELECT (ar.punch_time::date) as day, count(*) as total_punches, count(distinct ar.employee_id) as unique_employees_punched " +
                "FROM attendance_record ar " +
                "WHERE ar.deleted = false " +
                (companyId != null ? " AND ar.company_id = ? " : "") +
                " AND ar.punch_time >= ? AND ar.punch_time < ? " +
                "GROUP BY day ORDER BY day";

        if (companyId != null) {
            return jdbc.query(sql, new Object[]{companyId, from.atStartOfDay(), to.plusDays(1).atStartOfDay()}, (rs, rowNum) -> mapAttendanceSummary(rs));
        } else {
            return jdbc.query(sql, new Object[]{from.atStartOfDay(), to.plusDays(1).atStartOfDay()}, (rs, rowNum) -> mapAttendanceSummary(rs));
        }
    }

    private Map<String,Object> mapAttendanceSummary(ResultSet rs) throws SQLException {
        Map<String,Object> m = new HashMap<>();
        m.put("day", rs.getDate("day").toLocalDate());
        m.put("total_punches", rs.getLong("total_punches"));
        m.put("unique_employees_punched", rs.getLong("unique_employees_punched"));
        return m;
    }

    /** Working hours per employee in range */
    public List<Map<String,Object>> workingHoursByEmployee(Long companyId, LocalDate from, LocalDate to) {
        String sql = "SELECT ws.employee_id, e.employee_id as employee_code, e.first_name, e.last_name, " +
                "sum(coalesce(ws.total_working_minutes,0)) as total_minutes " +
                "FROM work_session ws " +
                "LEFT JOIN employee e ON e.id = ws.employee_id " +
                "WHERE ws.deleted = false " +
                (companyId != null ? " AND ws.company_id = ? " : "") +
                " AND ws.session_date >= ? AND ws.session_date <= ? " +
                "GROUP BY ws.employee_id, e.employee_id, e.first_name, e.last_name " +
                "ORDER BY total_minutes DESC";
        if (companyId != null) {
            return jdbc.query(sql, new Object[]{companyId, from, to}, (rs, rowNum) -> mapWorkingHours(rs));
        } else {
            return jdbc.query(sql, new Object[]{from, to}, (rs, rowNum) -> mapWorkingHours(rs));
        }
    }

    private Map<String,Object> mapWorkingHours(ResultSet rs) throws SQLException {
        Map<String,Object> m = new HashMap<>();
        m.put("employee_id", rs.getLong("employee_id"));
        m.put("employee_code", rs.getString("employee_code"));
        m.put("first_name", rs.getString("first_name"));
        m.put("last_name", rs.getString("last_name"));
        m.put("total_minutes", rs.getLong("total_minutes"));
        return m;
    }

    /** Active vs idle time aggregated by employee */
    public List<Map<String,Object>> activeIdleByEmployee(Long companyId, LocalDate from, LocalDate to) {
        String baseSql = "SELECT s.employee_id, e.employee_id as employee_code, e.first_name, e.last_name, " +
                "sum(CASE WHEN s.is_idle_session = false THEN s.duration_seconds ELSE 0 END) as active_seconds, " +
                "sum(CASE WHEN s.is_idle_session = true THEN s.duration_seconds ELSE 0 END) as idle_seconds " +
                "FROM activity_session s " +
                "LEFT JOIN employee e ON e.id = s.employee_id " +
                "WHERE s.deleted = false " +
                (companyId != null ? " AND s.company_id = ? " : "") +
                " AND s.start_time >= ? AND s.start_time < ? " +
                "GROUP BY s.employee_id, e.employee_id, e.first_name, e.last_name " +
                "ORDER BY active_seconds DESC";
        if (companyId != null) {
            return jdbc.query(baseSql, new Object[]{companyId, from.atStartOfDay(), to.plusDays(1).atStartOfDay()}, (rs, rowNum) -> mapActiveIdle(rs));
        } else {
            return jdbc.query(baseSql, new Object[]{from.atStartOfDay(), to.plusDays(1).atStartOfDay()}, (rs, rowNum) -> mapActiveIdle(rs));
        }
    }

    private Map<String,Object> mapActiveIdle(ResultSet rs) throws SQLException {
        Map<String,Object> m = new HashMap<>();
        m.put("employee_id", rs.getLong("employee_id"));
        m.put("employee_code", rs.getString("employee_code"));
        m.put("first_name", rs.getString("first_name"));
        m.put("last_name", rs.getString("last_name"));
        m.put("active_seconds", rs.getLong("active_seconds"));
        m.put("idle_seconds", rs.getLong("idle_seconds"));
        long active = rs.getLong("active_seconds");
        long idle = rs.getLong("idle_seconds");
        if (active + idle > 0) {
            double prod = (double) active / (active + idle);
            m.put("productivity", prod);
        } else {
            m.put("productivity", null);
        }
        return m;
    }

    /** Application usage aggregate */
    public List<Map<String,Object>> applicationUsage(Long companyId, LocalDate from, LocalDate to, int limit) {
        String sql = "SELECT coalesce(s.application_name, s.process_name) as app, sum(s.duration_seconds) as total_seconds, count(*) as sessions " +
                "FROM activity_session s " +
                "WHERE s.deleted = false " +
                (companyId != null ? " AND s.company_id = ? " : "") +
                " AND s.start_time >= ? AND s.start_time < ? " +
                "GROUP BY app ORDER BY total_seconds DESC LIMIT ?";
        Object[] params;
        if (companyId != null) params = new Object[]{companyId, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), limit};
        else params = new Object[]{from.atStartOfDay(), to.plusDays(1).atStartOfDay(), limit};
        return jdbc.query(sql, params, (rs, rowNum) -> mapAppUsage(rs));
    }

    private Map<String,Object> mapAppUsage(ResultSet rs) throws SQLException {
        Map<String,Object> m = new HashMap<>();
        m.put("application", rs.getString("app"));
        m.put("total_seconds", rs.getLong("total_seconds"));
        m.put("sessions", rs.getLong("sessions"));
        return m;
    }

    /** Hourly activity timeline: sum duration per hour */
    public List<Map<String,Object>> hourlyActivity(Long companyId, LocalDate date) {
        String sql = "SELECT date_trunc('hour', s.start_time) as hour, sum(s.duration_seconds) as total_seconds " +
                "FROM activity_session s " +
                "WHERE s.deleted = false " +
                (companyId != null ? " AND s.company_id = ? " : "") +
                " AND s.start_time >= ? AND s.start_time < ? " +
                "GROUP BY hour ORDER BY hour";
        if (companyId != null) return jdbc.query(sql, new Object[]{companyId, date.atStartOfDay(), date.plusDays(1).atStartOfDay()}, (rs, rowNum) -> {
            Map<String,Object> m = new HashMap<>();
            m.put("hour", rs.getTimestamp("hour").toLocalDateTime());
            m.put("total_seconds", rs.getLong("total_seconds"));
            return m;
        });
        else return jdbc.query(sql, new Object[]{date.atStartOfDay(), date.plusDays(1).atStartOfDay()}, (rs, rowNum) -> {
            Map<String,Object> m = new HashMap<>();
            m.put("hour", rs.getTimestamp("hour").toLocalDateTime());
            m.put("total_seconds", rs.getLong("total_seconds"));
            return m;
        });
    }

    /** Office vs WFH comparison */
    public Map<String,Object> officeVsWfh(Long companyId, LocalDate from, LocalDate to) {
        String sql = "SELECT ws.working_mode, count(*) as sessions, sum(coalesce(ws.total_working_minutes,0)) as minutes " +
                "FROM work_session ws " +
                "WHERE ws.deleted = false " +
                (companyId != null ? " AND ws.company_id = ? " : "") +
                " AND ws.session_date >= ? AND ws.session_date <= ? " +
                "GROUP BY ws.working_mode";
        Object[] params;
        if (companyId != null) params = new Object[]{companyId, from, to};
        else params = new Object[]{from, to};
        List<Map<String,Object>> rows = jdbc.query(sql, params, (rs, rowNum) -> {
            Map<String,Object> m = new HashMap<>();
            m.put("working_mode", rs.getString("working_mode"));
            m.put("sessions", rs.getLong("sessions"));
            m.put("minutes", rs.getLong("minutes"));
            return m;
        });
        Map<String,Object> out = new HashMap<>();
        out.put("rows", rows);
        return out;
    }

    /** Active, idle, and productivity totals grouped directly by department. */
    public List<Map<String,Object>> departmentComparison(Long companyId, LocalDate from, LocalDate to) {
        String sql = "SELECT coalesce(d.name, 'Unassigned') as department, " +
                "sum(CASE WHEN s.is_idle_session = false THEN s.duration_seconds ELSE 0 END) as active_seconds, " +
                "sum(CASE WHEN s.is_idle_session = true THEN s.duration_seconds ELSE 0 END) as idle_seconds " +
                "FROM activity_session s LEFT JOIN employee e ON e.id = s.employee_id " +
                "LEFT JOIN department d ON d.id = e.department_id WHERE s.deleted = false " +
                (companyId != null ? " AND s.company_id = ? " : "") +
                " AND s.start_time >= ? AND s.start_time < ? GROUP BY d.name ORDER BY active_seconds DESC";
        Object[] params = companyId != null
                ? new Object[]{companyId, from.atStartOfDay(), to.plusDays(1).atStartOfDay()}
                : new Object[]{from.atStartOfDay(), to.plusDays(1).atStartOfDay()};
        return jdbc.query(sql, params, (rs, rowNum) -> {
            long active = rs.getLong("active_seconds");
            long idle = rs.getLong("idle_seconds");
            Map<String,Object> row = new HashMap<>();
            row.put("department", rs.getString("department"));
            row.put("active_seconds", active);
            row.put("idle_seconds", idle);
            row.put("productivity", active + idle == 0 ? null : (double) active / (active + idle));
            return row;
        });
    }

}
