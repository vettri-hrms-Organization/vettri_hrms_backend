package com.haodaone.expense.repository;

import com.haodaone.expense.entity.ExpenseSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExpenseSettingsRepository extends JpaRepository<ExpenseSettings, Long> {

    Optional<ExpenseSettings> findFirstByCompany_IdAndDeletedFalse(Long companyId);
}
