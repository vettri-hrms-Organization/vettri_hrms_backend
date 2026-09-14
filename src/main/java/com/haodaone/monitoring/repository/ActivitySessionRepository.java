package com.haodaone.monitoring.repository;

import com.haodaone.monitoring.entity.ActivitySession;
import com.haodaone.monitoring.report.repository.ApplicationUsageProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivitySessionRepository extends JpaRepository<ActivitySession, Long> {

    /** De-duplication check for batch ingestion - see AgentIngestService#recordActivityBatch. */
    boolean existsBySessionId(String sessionId);

    boolean existsBySessionIdAndDevice_IdAndCompany_Id(String sessionId, Long deviceId, Long companyId);

    @Modifying
    @Query(value = """
            insert into activity_session
                (session_id, device_id, employee_id, company_id, process_name, application_name,
                 window_title, start_time, end_time, duration_seconds, is_idle_session)
            values
                (:sessionId, :deviceId, :employeeId, :companyId, :processName, :applicationName,
                 :windowTitle, :startTime, :endTime, :durationSeconds, :idleSession)
            on conflict (session_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("sessionId") String sessionId,
                       @Param("deviceId") Long deviceId,
                       @Param("employeeId") Long employeeId,
                       @Param("companyId") Long companyId,
                       @Param("processName") String processName,
                       @Param("applicationName") String applicationName,
                       @Param("windowTitle") String windowTitle,
                       @Param("startTime") LocalDateTime startTime,
                       @Param("endTime") LocalDateTime endTime,
                       @Param("durationSeconds") int durationSeconds,
                       @Param("idleSession") boolean idleSession);

    @Query("select s from ActivitySession s join s.device d where s.device.id = :deviceId and d.company.id = :companyId and d.deleted = false order by s.startTime desc")
    Page<ActivitySession> findByDevice_IdAndCompany_IdOrderByStartTimeDesc(@Param("deviceId") Long deviceId, @Param("companyId") Long companyId, Pageable pageable);

    @Query("select s from ActivitySession s join s.device d where s.employee.id = :employeeId and d.company.id = :companyId and d.deleted = false order by s.startTime desc")
    Page<ActivitySession> findByEmployee_IdAndCompany_IdOrderByStartTimeDesc(@Param("employeeId") Long employeeId, @Param("companyId") Long companyId, Pageable pageable);

    @Query("select s from ActivitySession s join s.device d where s.startTime between :from and :to and d.company.id = :companyId and d.deleted = false order by s.startTime desc")
    Page<ActivitySession> findByStartTimeBetweenOrderByStartTimeDesc(@Param("from") LocalDateTime from,
                                                                      @Param("to") LocalDateTime to,
                                                                      @Param("companyId") Long companyId,
                                                                      Pageable pageable);

    @Query("select count(s) from ActivitySession s join s.device d where s.device.id = :deviceId and d.deleted = false and s.idleSession = false and s.startTime > :after")
    long countByDevice_IdAndIdleSessionFalseAndStartTimeAfter(@Param("deviceId") Long deviceId,
                                                               @Param("after") LocalDateTime after);

    /**
     * Backs every report/productivity/export endpoint in
     * monitoring.report.* - every filter is optional except the date
     * range, which is always supplied (bounded so a report can't
     * accidentally scan the whole table). Employee/department filters
     * tolerate a null employee on the session (unassigned devices) by
     * simply excluding those rows once any employee-scoped filter is set,
     * same as an inner join would.
     *
     * IMPORTANT - do not wrap :employeeNamePattern / :deviceNamePattern in
     * concat()/lower() here. See ProductivityReportService#fetchSessions
     * for why: those two parameters must arrive pre-built ("%value%",
     * already lower-cased) and be compared directly via `ilike` against a
     * mapped String expression, or Hibernate 6 cannot infer a JDBC type for
     * them and silently binds them as bytea, which blows up every report
     * endpoint with "function lower(bytea) does not exist" /
     * "operator does not exist: text ~~ bytea".
     */
    @Query("""
select s
from ActivitySession s
join s.device d
left join s.employee e
where s.startTime >= :from
and s.startTime < :to
and d.deleted = false
and d.company.id = :companyId
and (:employeeId is null or e.id = :employeeId)
and (:employeeCode is null or e.employeeCode = :employeeCode)
and (:employeeNamePattern is null or concat(e.firstName, ' ', e.lastName) ilike :employeeNamePattern)
and (:departmentId is null or e.department.id = :departmentId)
and (:deviceId is null or d.id = :deviceId)
and (:deviceNamePattern is null or d.deviceName ilike :deviceNamePattern)
and (:applicationNamePattern is null or s.applicationName ilike :applicationNamePattern)
order by s.startTime asc
""")
    List<ActivitySession> search(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId,
            @Param("employeeCode") String employeeCode,
            @Param("employeeNamePattern") String employeeNamePattern,
            @Param("departmentId") Long departmentId,
            @Param("deviceId") Long deviceId,
            @Param("deviceNamePattern") String deviceNamePattern
            , @Param("applicationNamePattern") String applicationNamePattern
    );

        @Query(value = """
            select s.employee_id as employeeId, s.device_id as deviceId,
               cast(s.start_time as date) as usageDate,
               coalesce(s.application_name, case when s.is_idle_session then 'Idle' else 'Unknown Application' end) as applicationName,
               coalesce(nullif(trim(s.window_title), ''), '(No window title)') as windowTitle,
               sum(s.duration_seconds) as seconds,
               bool_or(s.is_idle_session) as idle
            from activity_session s
            join monitored_device d on d.id = s.device_id
                        left join employee e on e.id = s.employee_id
            where s.start_time >= :from and s.start_time < :to
                            and d.deleted = false
                                                            and d.company_id = :companyId
                              and (cast(:employeeId as bigint) is null or s.employee_id = cast(:employeeId as bigint))
                              and (cast(:employeeCode as text) is null or e.employee_id = cast(:employeeCode as text))
                              and (cast(:employeeNamePattern as text) is null or concat(e.first_name, ' ', e.last_name) ilike cast(:employeeNamePattern as text))
                              and (cast(:departmentId as bigint) is null or e.department_id = cast(:departmentId as bigint))
                              and (cast(:deviceId as bigint) is null or d.id = cast(:deviceId as bigint))
                              and (cast(:deviceNamePattern as text) is null or d.device_name ilike cast(:deviceNamePattern as text))
                              and (cast(:applicationNamePattern as text) is null or s.application_name ilike cast(:applicationNamePattern as text))
            group by s.employee_id, s.device_id, cast(s.start_time as date),
                 s.application_name, s.is_idle_session, s.window_title
            order by usageDate desc, seconds desc
            """, nativeQuery = true)
        List<ApplicationUsageProjection> searchApplicationUsageGrouped(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId,
            @Param("employeeCode") String employeeCode,
            @Param("employeeNamePattern") String employeeNamePattern,
            @Param("departmentId") Long departmentId,
            @Param("deviceId") Long deviceId,
            @Param("deviceNamePattern") String deviceNamePattern,
            @Param("applicationNamePattern") String applicationNamePattern
        );

    /**
     * Paginated counterpart of search() for the Activity page - same
     * filters (date range + employeeId + employeeCode + deviceId), same
     * ilike-not-concat rule applies to :employeeCode, but here it's an
     * exact match (Activity page filters by the same Employee ID/code
     * shown in the Device Assignment table), not a fuzzy pattern, so no
     * pattern-building is needed for it.
     *
     * This is the query the Activity page's "no activity" bug traced back
     * to: previously there was no single endpoint that could combine a
     * date range with an employee/device filter at all (see
     * ActivitySessionController - byDevice / byEmployee / byDateRange were
     * three mutually-exclusive methods), so the frontend's employeeCode
     * filter, if wired up, had nowhere valid to go.
     */
    @Query(value = """
select s
from ActivitySession s
join s.device d
left join s.employee e
where s.startTime >= :from
and s.startTime < :to
and d.deleted = false
and d.company.id = :companyId
and (:employeeId is null or e.id = :employeeId)
and (:employeeCode is null or e.employeeCode = :employeeCode)
and (:deviceId is null or d.id = :deviceId)
and (:windowTitle is null or s.windowTitle ilike :windowTitle)
order by s.startTime desc
""",
            countQuery = """
select count(s)
from ActivitySession s
join s.device d
left join s.employee e
where s.startTime >= :from
and s.startTime < :to
and d.deleted = false
and d.company.id = :companyId
and (:employeeId is null or e.id = :employeeId)
and (:employeeCode is null or e.employeeCode = :employeeCode)
and (:deviceId is null or d.id = :deviceId)
and (:windowTitle is null or s.windowTitle ilike :windowTitle)
""")
    Page<ActivitySession> searchPaged(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId,
            @Param("employeeCode") String employeeCode,
            @Param("deviceId") Long deviceId,
            @Param("windowTitle") String windowTitle,
            Pageable pageable
    );
}