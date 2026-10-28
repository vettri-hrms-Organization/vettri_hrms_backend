package com.haodaone.customfield.repository;

import com.haodaone.customfield.entity.CustomFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomFieldDefinitionRepository extends JpaRepository<CustomFieldDefinition, Long> {

    List<CustomFieldDefinition> findAllByCompany_IdAndDeletedFalseOrderBySortOrderAscIdAsc(Long companyId);

    Optional<CustomFieldDefinition> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    boolean existsByCompany_IdAndFieldKeyAndDeletedFalse(Long companyId, String fieldKey);
}
