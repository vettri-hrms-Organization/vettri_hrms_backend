package com.haodaone.performance.suite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedbackNoteRepository extends JpaRepository<FeedbackNote, Long> {
    List<FeedbackNote> findTop100ByCompanyIdAndVisibilityAndDeletedFalseOrderByCreatedAtDesc(Long companyId, String visibility);
    List<FeedbackNote> findAllByCompanyIdAndToEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(Long companyId, Long toEmployeeId);
    List<FeedbackNote> findAllByCompanyIdAndFromEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(Long companyId, Long fromEmployeeId);
}
