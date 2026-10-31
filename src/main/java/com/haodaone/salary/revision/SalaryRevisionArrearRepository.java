package com.haodaone.salary.revision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalaryRevisionArrearRepository extends JpaRepository<SalaryRevisionArrear, Long> {
    List<SalaryRevisionArrear> findAllByRevisionIdAndDeletedFalseOrderByPeriodAsc(Long revisionId);
}
