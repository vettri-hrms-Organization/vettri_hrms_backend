package com.haodaone.notifications.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyWhatsAppSettingRepository extends JpaRepository<CompanyWhatsAppSetting, Long> {

    Optional<CompanyWhatsAppSetting> findByCompany_IdAndDeletedFalse(Long companyId);
}
