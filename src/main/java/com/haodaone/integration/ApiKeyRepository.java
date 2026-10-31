package com.haodaone.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    List<ApiKey> findAllByCompanyIdAndDeletedFalseOrderByIdDesc(Long companyId);
    Optional<ApiKey> findByKeyHashAndDeletedFalse(String keyHash);
    Optional<ApiKey> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
