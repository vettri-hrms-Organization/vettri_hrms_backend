package com.haodaone.leave.repository;

import com.haodaone.leave.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    List<Holiday> findAllByDeletedFalseOrderByDateAsc();
    boolean existsByDateAndDeletedFalse(LocalDate date);
    List<Holiday> findAllByDateBetweenAndDeletedFalse(LocalDate start, LocalDate end);
    List<Holiday> findAllByCompany_IdAndDateBetweenAndDeletedFalse(Long companyId, LocalDate start, LocalDate end);
    boolean existsByCompany_IdAndDateAndDeletedFalse(Long companyId, LocalDate date);
    java.util.Optional<Holiday> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
}
