package com.haodaone.salary.bank;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollBankSettingsRepository extends JpaRepository<PayrollBankSettings, Long> {
    Optional<PayrollBankSettings> findByCompanyIdAndDeletedFalse(Long companyId);
}
