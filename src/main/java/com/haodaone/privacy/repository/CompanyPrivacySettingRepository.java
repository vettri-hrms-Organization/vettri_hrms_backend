package com.haodaone.privacy.repository;

import com.haodaone.privacy.entity.CompanyPrivacySetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyPrivacySettingRepository extends JpaRepository<CompanyPrivacySetting, Long> {

    Optional<CompanyPrivacySetting> findByCompany_IdAndDeletedFalse(Long companyId);
}
