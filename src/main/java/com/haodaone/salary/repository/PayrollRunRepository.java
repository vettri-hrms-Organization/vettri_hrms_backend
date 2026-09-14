package com.haodaone.salary.repository;

import com.haodaone.salary.entity.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

    Optional<PayrollRun> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    Optional<PayrollRun> findByPeriodYearAndPeriodMonthAndCompany_IdAndDeletedFalse(
            int periodYear,
            int periodMonth,
            Long companyId
    );

    List<PayrollRun> findAllByCompany_IdAndDeletedFalseOrderByPeriodYearDescPeriodMonthDesc(
            Long companyId
    );

    Optional<PayrollRun> findByIdAndDeletedFalse(Long id);

    Optional<PayrollRun> findByPeriodYearAndPeriodMonthAndDeletedFalse(
            int periodYear,
            int periodMonth
    );

    List<PayrollRun> findAllByDeletedFalseOrderByPeriodYearDescPeriodMonthDesc();

    /** Most recently created run regardless of status - "this period's" numbers for the dashboard KPI strip. */
    Optional<PayrollRun> findTopByCompany_IdAndDeletedFalseOrderByPeriodYearDescPeriodMonthDesc(
            Long companyId
    );

    /** Up to the last 12 processed/paid runs, oldest first, for the payroll trend chart. */
    @Query("select r from PayrollRun r " +
            "where r.deleted = false " +
            "and r.company.id = :companyId " +
            "and r.status <> 'DRAFT' " +
            "and r.status <> 'CANCELLED' " +
            "order by r.periodYear desc, r.periodMonth desc")
    List<PayrollRun> findRecentNonDraftRuns(
            @Param("companyId") Long companyId
    );

    /** The soonest upcoming pay date among runs still awaiting payment - drives "Upcoming Payroll Date". */
    @Query("select min(r.payDate) from PayrollRun r " +
            "where r.deleted = false " +
            "and r.company.id = :companyId " +
            "and r.status in ('DRAFT','PROCESSED') " +
            "and r.payDate >= :today")
    LocalDate findNextUpcomingPayDate(
            @Param("companyId") Long companyId,
            @Param("today") LocalDate today
    );
}