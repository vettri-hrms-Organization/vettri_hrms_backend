package com.haodaone.letters.repository;

import com.haodaone.letters.entity.LetterTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LetterTemplateRepository extends JpaRepository<LetterTemplate, Long> {
    List<LetterTemplate> findAllByCompany_IdAndDeletedFalseOrderByNameAsc(Long companyId);

    Optional<LetterTemplate> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    Optional<LetterTemplate> findFirstByCompany_IdAndLetterTypeAndActiveTrueAndDeletedFalseOrderByIdAsc(Long companyId, String letterType);

    boolean existsByCompany_Id(Long companyId);
}
