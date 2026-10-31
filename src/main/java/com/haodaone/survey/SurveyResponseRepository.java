package com.haodaone.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {
    List<SurveyResponse> findAllBySurveyIdAndDeletedFalse(Long surveyId);
    boolean existsBySurveyIdAndRespondentKey(Long surveyId, String respondentKey);
    long countBySurveyIdAndDeletedFalse(Long surveyId);
}
