package com.haodaone.monitoring.report.service;

import com.haodaone.monitoring.entity.ActivitySession;
import com.haodaone.monitoring.report.dto.AppUsageDTO;
import com.haodaone.monitoring.report.dto.ManagementInsightsDTO;
import com.haodaone.monitoring.report.dto.ProductivitySummaryDTO;
import com.haodaone.monitoring.report.dto.ReportFilter;
import com.haodaone.monitoring.repository.ActivitySessionRepository;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.attendance.entity.WorkSession;
import com.haodaone.attendance.repository.WorkSessionRepository;
import com.haodaone.tenant.TenantContext;
import com.haodaone.monitoring.report.repository.ApplicationUsageProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Turns raw ActivitySession rows (real agent-reported data - never mock)
 * into the Productivity Summary / Activity Report / Management View shapes
 * the frontend and the Excel/PDF exporters all share. Every number here is
 * derived directly from activity_session; nothing is fabricated.
 */
@Service
@Transactional(readOnly = true)
public class ProductivityReportService {

    /**
     * An idle session at or above this length counts as Break Time rather
     * than Idle Time - see ProductivitySummaryDTO's javadoc for the
     * reasoning. 15 minutes is a common default for "stepped away" vs
     * "momentarily idle at the desk"; tune here if HR wants a different cut.
     */
    private static final long BREAK_THRESHOLD_SECONDS = 15 * 60;

    private static final int TOP_APPS_LIMIT = 50;
    private static final int RANKING_LIMIT = 10;

    private final ActivitySessionRepository activitySessionRepository;
    private final CompanyRepository companyRepository;
    private final WorkSessionRepository workSessionRepository;

    public ProductivityReportService(ActivitySessionRepository activitySessionRepository, CompanyRepository companyRepository, WorkSessionRepository workSessionRepository) {
        this.activitySessionRepository = activitySessionRepository;
        this.companyRepository = companyRepository;
        this.workSessionRepository = workSessionRepository;
    }

    /** Every row of the filtered report, one per employee/device/day - backs both the Activity Report table and the Productivity Summary table. */
    public List<ProductivitySummaryDTO> buildSummary(ReportFilter filter) {
        List<ActivitySession> sessions = fetchSessions(filter);
        Map<String, List<ApplicationUsageProjection>> usage = fetchApplicationUsage(filter);
        Map<String, List<ActivitySession>> grouped = groupByEmployeeDeviceDay(sessions);

        List<ProductivitySummaryDTO> rows = new ArrayList<>();
        for (List<ActivitySession> group : grouped.values()) {
            rows.add(summarize(group, usage.get(summaryKey(group))));
        }
        rows.sort(Comparator.comparing(ProductivitySummaryDTO::getDate).reversed()
                .thenComparing(dto -> Objects.toString(dto.getEmployeeName(), "")));
        return rows;
    }

    /** Management View's leaderboards and org-wide averages, computed over the same filtered rows as buildSummary. */
    public ManagementInsightsDTO buildManagementInsights(ReportFilter filter) {
        List<ProductivitySummaryDTO> rows = buildSummary(filter);

        // Aggregate per-employee across every day in range so a leaderboard
        // reflects the whole window, not just one day.
        Map<Long, List<ProductivitySummaryDTO>> byEmployee = new LinkedHashMap<>();
        for (ProductivitySummaryDTO row : rows) {
            if (row.getEmployeeId() == null) continue;
            byEmployee.computeIfAbsent(row.getEmployeeId(), k -> new ArrayList<>()).add(row);
        }

        List<ManagementInsightsDTO.EmployeeRanking> mostActive = new ArrayList<>();
        List<ManagementInsightsDTO.EmployeeRanking> highestIdle = new ArrayList<>();
        List<ManagementInsightsDTO.EmployeeRanking> productivity = new ArrayList<>();

        double totalLoggedInHours = 0;
        double totalProductivity = 0;
        int dayCount = 0;

        for (Map.Entry<Long, List<ProductivitySummaryDTO>> entry : byEmployee.entrySet()) {
            List<ProductivitySummaryDTO> days = entry.getValue();
            ProductivitySummaryDTO first = days.get(0);

            double activeHours = days.stream().mapToLong(ProductivitySummaryDTO::getActiveSeconds).sum() / 3600.0;
            double idleAndBreakHours = days.stream().mapToLong(d -> d.getIdleSeconds() + d.getBreakSeconds()).sum() / 3600.0;
            double avgProductivity = days.stream().mapToDouble(ProductivitySummaryDTO::getProductivityPercent).average().orElse(0);

            mostActive.add(new ManagementInsightsDTO.EmployeeRanking(first.getEmployeeId(), first.getEmployeeCode(),
                    first.getEmployeeName(), first.getDepartmentName(), round2(activeHours)));
            highestIdle.add(new ManagementInsightsDTO.EmployeeRanking(first.getEmployeeId(), first.getEmployeeCode(),
                    first.getEmployeeName(), first.getDepartmentName(), round2(idleAndBreakHours)));
            productivity.add(new ManagementInsightsDTO.EmployeeRanking(first.getEmployeeId(), first.getEmployeeCode(),
                    first.getEmployeeName(), first.getDepartmentName(), round2(avgProductivity)));

            for (ProductivitySummaryDTO d : days) {
                totalLoggedInHours += d.getTotalLoggedInSeconds() / 3600.0;
                totalProductivity += d.getProductivityPercent();
                dayCount++;
            }
        }

        mostActive.sort(Comparator.comparingDouble(ManagementInsightsDTO.EmployeeRanking::getValue).reversed());
        highestIdle.sort(Comparator.comparingDouble(ManagementInsightsDTO.EmployeeRanking::getValue).reversed());
        productivity.sort(Comparator.comparingDouble(ManagementInsightsDTO.EmployeeRanking::getValue).reversed());

        ManagementInsightsDTO dto = new ManagementInsightsDTO();
        dto.setMostActiveEmployees(cap(mostActive));
        dto.setHighestIdleEmployees(cap(highestIdle));
        dto.setProductivityRanking(cap(productivity));
        dto.setAverageWorkingHours(dayCount > 0 ? round2(totalLoggedInHours / dayCount) : 0);
        dto.setAverageProductivityPercent(dayCount > 0 ? round2(totalProductivity / dayCount) : 0);
        dto.setEmployeeDaysAnalyzed(dayCount);
        return dto;
    }

    private List<ManagementInsightsDTO.EmployeeRanking> cap(List<ManagementInsightsDTO.EmployeeRanking> list) {
        return list.size() > RANKING_LIMIT ? new ArrayList<>(list.subList(0, RANKING_LIMIT)) : list;
    }

    /**
     * Builds the wildcard patterns in Java, never in JPQL - see
     * ActivitySessionRepository#search's javadoc for exactly why. Both
     * patterns are pre-lower-cased and combined with `ilike` at the SQL
     * layer, so this method's own case-folding here is just for
     * readability/consistency, not required for correctness.
     */
    List<ActivitySession> fetchSessions(ReportFilter filter) {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) return List.of();
        LocalDateTime from = LocalDateTime.of(filter.getStartDate(), filter.getFromTime() != null
            ? filter.getFromTime() : LocalTime.MIN);
        LocalDateTime to = LocalDateTime.of(filter.getEndDate(), filter.getToTime() != null
            ? filter.getToTime() : LocalTime.MAX);
        if (filter.getToTime() == null) to = to.plusNanos(1);
        String employeeNamePattern = toPattern(filter.getEmployeeName());
        String deviceNamePattern = toPattern(filter.getDeviceName());
        String applicationNamePattern = toPattern(filter.getApplicationName());
        List<ActivitySession> sessions = activitySessionRepository.search(from, to, companyId, filter.getEmployeeId(), filter.getEmployeeCode(),
            employeeNamePattern, filter.getDepartmentId(), filter.getDeviceId(), deviceNamePattern, applicationNamePattern);
        return filterByWorkingMode(sessions, filter, companyId);
    }

    private Map<String, List<ApplicationUsageProjection>> fetchApplicationUsage(ReportFilter filter) {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) return Map.of();
        LocalDateTime from = LocalDateTime.of(filter.getStartDate(), filter.getFromTime() != null ? filter.getFromTime() : LocalTime.MIN);
        LocalDateTime to = LocalDateTime.of(filter.getEndDate(), filter.getToTime() != null ? filter.getToTime() : LocalTime.MAX);
        if (filter.getToTime() == null) to = to.plusNanos(1);
        Map<String, List<ApplicationUsageProjection>> grouped = new LinkedHashMap<>();
        Set<String> allowedEmployeeDates = allowedEmployeeDates(filter, companyId);
        for (ApplicationUsageProjection row : activitySessionRepository.searchApplicationUsageGrouped(from, to, companyId,
                filter.getEmployeeId(), filter.getEmployeeCode(), toPattern(filter.getEmployeeName()), filter.getDepartmentId(),
                filter.getDeviceId(), toPattern(filter.getDeviceName()), toPattern(filter.getApplicationName()))) {
            if (filter.getWorkingMode() == null || allowedEmployeeDates.contains(row.getEmployeeId() + "|" + row.getUsageDate())) {
                grouped.computeIfAbsent(row.getEmployeeId() + "|" + row.getDeviceId() + "|" + row.getUsageDate(), key -> new ArrayList<>()).add(row);
            }
        }
        return grouped;
    }

    private List<ActivitySession> filterByWorkingMode(List<ActivitySession> sessions, ReportFilter filter, Long companyId) {
        if (filter.getWorkingMode() == null) return sessions;
        String mode = filter.getWorkingMode().toUpperCase();
        if (!mode.equals("OFFICE") && !mode.equals("WFH")) return List.of();
        Set<String> employeeDates = allowedEmployeeDates(filter, companyId);
        return sessions.stream().filter(session -> session.getEmployee() != null
            && employeeDates.contains(session.getEmployee().getId() + "|" + session.getStartTime().toLocalDate())).toList();
        }

        private Set<String> allowedEmployeeDates(ReportFilter filter, Long companyId) {
        if (filter.getWorkingMode() == null) return Set.of();
        String mode = filter.getWorkingMode().toUpperCase();
        if (!mode.equals("OFFICE") && !mode.equals("WFH")) return Set.of();
        return workSessionRepository
                .findAllByCompany_IdAndWorkingModeAndSessionDateBetween(companyId, mode, filter.getStartDate(), filter.getEndDate())
                .stream().map(this::workSessionKey).collect(java.util.stream.Collectors.toSet());
    }

    private String workSessionKey(WorkSession session) {
        return session.getEmployee().getId() + "|" + session.getSessionDate();
    }

    private String toPattern(String rawValue) {
        return (rawValue == null || rawValue.isBlank()) ? null : "%" + rawValue.trim() + "%";
    }

    private Map<String, List<ActivitySession>> groupByEmployeeDeviceDay(List<ActivitySession> sessions) {
        Map<String, List<ActivitySession>> grouped = new LinkedHashMap<>();
        for (ActivitySession s : sessions) {
            Long employeeId = s.getEmployee() != null ? s.getEmployee().getId() : null;
            String key = employeeId + "|" + s.getDevice().getId() + "|" + s.getStartTime().toLocalDate();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        return grouped;
    }

    private ProductivitySummaryDTO summarize(List<ActivitySession> group, List<ApplicationUsageProjection> usageRows) {
        ActivitySession any = group.get(0);

        long activeSeconds = 0;
        long idleSeconds = 0;
        long breakSeconds = 0;
        LocalDateTime login = null;
        LocalDateTime logout = null;

        for (ActivitySession s : group) {
            long duration = s.getDurationSeconds();
            LocalDateTime start = s.getStartTime();
            LocalDateTime end = s.getEndTime() != null ? s.getEndTime() : start.plusSeconds(duration);

            if (login == null || start.isBefore(login)) login = start;
            if (logout == null || end.isAfter(logout)) logout = end;

            if (s.isIdleSession()) {
                if (duration >= BREAK_THRESHOLD_SECONDS) {
                    breakSeconds += duration;
                } else {
                    idleSeconds += duration;
                }
            } else {
                activeSeconds += duration;
            }

            String appName = s.getApplicationName() != null ? s.getApplicationName()
                    : (s.isIdleSession() ? "Idle" : "Unknown Application");
        }

        long loggedIn = activeSeconds + idleSeconds + breakSeconds;
        double productivity = loggedIn > 0 ? round2((activeSeconds * 100.0) / loggedIn) : 0;
        Company company = TenantContext.getCurrentTenant() == null ? null : companyRepository.findById(TenantContext.getCurrentTenant()).orElse(null);
        int productiveThreshold = company != null && company.getProductiveThresholdPercent() != null ? company.getProductiveThresholdPercent() : 80;
        int neutralThreshold = company != null && company.getNeutralThresholdPercent() != null ? company.getNeutralThresholdPercent() : 50;

        List<AppUsageDTO> topApps = (usageRows == null ? List.<ApplicationUsageProjection>of() : usageRows).stream()
                .limit(TOP_APPS_LIMIT)
            .map(e -> new AppUsageDTO(e.getApplicationName(), e.getWindowTitle(), e.getSeconds(), Boolean.TRUE.equals(e.getIdle())))
                .toList();

        ProductivitySummaryDTO dto = new ProductivitySummaryDTO();
        if (any.getEmployee() != null) {
            dto.setEmployeeId(any.getEmployee().getId());
            dto.setEmployeeCode(any.getEmployee().getEmployeeCode());
            dto.setEmployeeName(any.getEmployee().getFullName());
            dto.setDepartmentName(any.getEmployee().getDepartment() != null ? any.getEmployee().getDepartment().getName() : null);
            dto.setDesignationTitle(any.getEmployee().getDesignation() != null ? any.getEmployee().getDesignation().getTitle() : null);
        }
        dto.setDeviceId(any.getDevice().getId());
        dto.setDeviceName(any.getDevice().getDeviceName());
        dto.setDate(any.getStartTime().toLocalDate());
        dto.setLoginTime(login);
        dto.setLogoutTime(logout);
        dto.setActiveSeconds(activeSeconds);
        dto.setIdleSeconds(idleSeconds);
        dto.setBreakSeconds(breakSeconds);
        dto.setTotalLoggedInSeconds(loggedIn);
        dto.setProductivityPercent(productivity);
        dto.setProductivityClassification(productivity >= productiveThreshold ? "PRODUCTIVE" : productivity >= neutralThreshold ? "NEUTRAL" : "NON_PRODUCTIVE");
        dto.setTopApplications(topApps);
        return dto;
    }

    private String summaryKey(List<ActivitySession> group) {
        ActivitySession first = group.get(0);
        return (first.getEmployee() != null ? first.getEmployee().getId() : null) + "|"
                + first.getDevice().getId() + "|" + first.getStartTime().toLocalDate();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
