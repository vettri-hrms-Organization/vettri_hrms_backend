package com.haodaone.auth.repository;

import com.haodaone.auth.entity.CompanySecuritySetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanySecuritySettingRepository extends JpaRepository<CompanySecuritySetting, Long> {

    Optional<CompanySecuritySetting> findByCompany_IdAndDeletedFalse(Long companyId);
}
