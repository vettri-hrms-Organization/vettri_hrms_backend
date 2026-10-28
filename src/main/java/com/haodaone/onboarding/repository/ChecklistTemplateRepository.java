package com.haodaone.onboarding.repository;

import com.haodaone.onboarding.entity.ChecklistTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, Long> {
    List<ChecklistTemplate> findAllByCompany_IdAndDeletedFalseOrderByTemplateTypeAscNameAsc(Long companyId);

    Optional<ChecklistTemplate> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    Optional<ChecklistTemplate> findFirstByCompany_IdAndTemplateTypeAndDefaultTemplateTrueAndActiveTrueAndDeletedFalseOrderByIdAsc(
            Long companyId, String templateType);

    boolean existsByCompany_IdAndTemplateType(Long companyId, String templateType);
}
