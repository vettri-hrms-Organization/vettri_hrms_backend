package com.haodaone.letters.repository;

import com.haodaone.letters.entity.LetterheadSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LetterheadSettingsRepository extends JpaRepository<LetterheadSettings, Long> {
    Optional<LetterheadSettings> findFirstByCompany_IdAndDeletedFalseOrderByIdAsc(Long companyId);
}
