package com.haodaone.sso.repository;

import com.haodaone.sso.entity.CompanySsoSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanySsoSettingRepository extends JpaRepository<CompanySsoSetting, Long> {

    Optional<CompanySsoSetting> findByCompany_IdAndDeletedFalse(Long companyId);

    List<CompanySsoSetting> findAllByAutoProvisionEnabledTrueAndDeletedFalse();
}
