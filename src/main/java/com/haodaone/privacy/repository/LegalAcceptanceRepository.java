package com.haodaone.privacy.repository;

import com.haodaone.privacy.entity.LegalAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, Long> {

    boolean existsByUser_IdAndDocTypeAndDocVersionAndDeletedFalse(Long userId, String docType, String docVersion);

    List<LegalAcceptance> findAllByUser_IdAndDeletedFalseOrderByAcceptedAtDesc(Long userId);

    List<LegalAcceptance> findAllByCompany_IdAndDeletedFalseOrderByAcceptedAtDesc(Long companyId);
}
