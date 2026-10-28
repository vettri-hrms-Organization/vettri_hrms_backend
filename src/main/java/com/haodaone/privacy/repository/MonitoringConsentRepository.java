package com.haodaone.privacy.repository;

import com.haodaone.privacy.entity.MonitoringConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonitoringConsentRepository extends JpaRepository<MonitoringConsent, Long> {

    Optional<MonitoringConsent> findFirstByCompany_IdAndEmployee_IdAndStatusAndDeletedFalseOrderByGrantedAtDesc(
            Long companyId, Long employeeId, String status);

    List<MonitoringConsent> findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByGrantedAtDesc(Long companyId, Long employeeId);

    List<MonitoringConsent> findAllByCompany_IdAndDeletedFalseOrderByGrantedAtDesc(Long companyId);
}
