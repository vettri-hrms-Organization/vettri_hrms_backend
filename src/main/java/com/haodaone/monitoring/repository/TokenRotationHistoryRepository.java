package com.haodaone.monitoring.repository;

import com.haodaone.monitoring.entity.TokenRotationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TokenRotationHistoryRepository extends JpaRepository<TokenRotationHistory, Long> {
    List<TokenRotationHistory> findAllByDevice_IdAndCompany_IdOrderByCreatedAtDesc(Long deviceId, Long companyId);
}