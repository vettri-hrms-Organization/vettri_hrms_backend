package com.haodaone.company.repository;

import com.haodaone.company.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByCompany_IdAndDeletedFalse(Long companyId);
}
