package com.haodaone.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HelpdeskCategoryRepository extends JpaRepository<HelpdeskCategory, Long> {
    List<HelpdeskCategory> findAllByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);
    Optional<HelpdeskCategory> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
