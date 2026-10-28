package com.haodaone.imports.repository;

import com.haodaone.imports.entity.DataImportBatch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DataImportBatchRepository extends JpaRepository<DataImportBatch, Long> {

    List<DataImportBatch> findAllByCompany_IdAndImportTypeInAndDeletedFalseOrderByCreatedAtDescIdDesc(
            Long companyId, Collection<String> importTypes, Pageable pageable);

    Optional<DataImportBatch> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    Optional<DataImportBatch> findFirstByCompany_IdAndImportTypeAndFileHashAndImportedRowsGreaterThanAndDeletedFalseOrderByCreatedAtDesc(
            Long companyId, String importType, String fileHash, int importedRows);
}
