package com.haodaone.salary.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollLedgerSettingsRepository extends JpaRepository<PayrollLedgerSettings, Long> {
    Optional<PayrollLedgerSettings> findByCompany_IdAndDeletedFalse(Long companyId);
}
