package com.haodaone.salary.lwf;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LwfContributionRepository extends JpaRepository<LwfContribution, Long> {
    List<LwfContribution> findAllByCompanyIdAndPeriodAndDeletedFalseOrderByStateCodeAsc(Long companyId, String period);
    boolean existsByEmployeeIdAndPeriodAndDeletedFalse(Long employeeId, String period);
}
