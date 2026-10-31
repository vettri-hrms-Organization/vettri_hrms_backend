package com.haodaone.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {
    List<WebhookEndpoint> findAllByCompanyIdAndDeletedFalseOrderByIdAsc(Long companyId);
    Optional<WebhookEndpoint> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
