package com.haodaone.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SurveyRepository extends JpaRepository<Survey, Long> {
    List<Survey> findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(Long companyId);
    Optional<Survey> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
    List<Survey> findAllByStatusAndClosesOnBeforeAndDeletedFalse(String status, LocalDate date);
}
