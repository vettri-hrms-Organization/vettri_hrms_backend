package com.haodaone.privacy.repository;

import com.haodaone.privacy.entity.LegalDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, Long> {

    Optional<LegalDocument> findByDocTypeAndDocVersionAndDeletedFalse(String docType, String docVersion);

    List<LegalDocument> findAllByDocTypeAndDeletedFalse(String docType);

    List<LegalDocument> findAllByDeletedFalseOrderByDocTypeAscPublishedAtDesc();
}
