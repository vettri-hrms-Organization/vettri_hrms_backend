package com.haodaone.notifications.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WhatsAppDeliveryLogRepository extends JpaRepository<WhatsAppDeliveryLog, Long> {

    List<WhatsAppDeliveryLog> findTop100ByCompanyIdOrderByCreatedAtDescIdDesc(Long companyId);
}
