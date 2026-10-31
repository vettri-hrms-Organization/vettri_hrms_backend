package com.haodaone.salary.lwf;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LwfRuleRepository extends JpaRepository<LwfRule, Long> {
    List<LwfRule> findAllByCompanyIdAndDeletedFalseOrderByStateNameAsc(Long companyId);
    Optional<LwfRule> findByCompanyIdAndStateCodeAndDeletedFalse(Long companyId, String stateCode);
    Optional<LwfRule> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
