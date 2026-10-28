package com.haodaone.letters.repository;

import com.haodaone.letters.entity.IssuedLetter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssuedLetterRepository extends JpaRepository<IssuedLetter, Long> {
    List<IssuedLetter> findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByIssuedAtDesc(Long companyId, Long employeeId);

    List<IssuedLetter> findAllByCompany_IdAndCandidateIdAndDeletedFalseOrderByIssuedAtDesc(Long companyId, Long candidateId);

    List<IssuedLetter> findTop100ByCompany_IdAndDeletedFalseOrderByIssuedAtDesc(Long companyId);

    Optional<IssuedLetter> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
}
