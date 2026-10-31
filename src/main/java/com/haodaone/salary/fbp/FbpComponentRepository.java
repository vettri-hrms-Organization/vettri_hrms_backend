package com.haodaone.salary.fbp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FbpComponentRepository extends JpaRepository<FbpComponent, Long> {
    List<FbpComponent> findAllByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);
    Optional<FbpComponent> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
