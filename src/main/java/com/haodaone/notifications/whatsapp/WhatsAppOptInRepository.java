package com.haodaone.notifications.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhatsAppOptInRepository extends JpaRepository<WhatsAppOptIn, Long> {

    Optional<WhatsAppOptIn> findByUser_IdAndDeletedFalse(Long userId);

    Optional<WhatsAppOptIn> findByUser_IdAndCompany_IdAndDeletedFalse(Long userId, Long companyId);
}
