package com.haodaone.privacy.repository;

import com.haodaone.privacy.entity.DataPrincipalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DataPrincipalRequestRepository extends JpaRepository<DataPrincipalRequest, Long> {

    List<DataPrincipalRequest> findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByCreatedAtDesc(Long companyId, Long employeeId);

    List<DataPrincipalRequest> findAllByCompany_IdAndDeletedFalseOrderByDueDateAscIdAsc(Long companyId);

    Optional<DataPrincipalRequest> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
}
