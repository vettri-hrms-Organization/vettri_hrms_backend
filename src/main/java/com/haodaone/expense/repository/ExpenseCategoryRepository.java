package com.haodaone.expense.repository;

import com.haodaone.expense.entity.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    List<ExpenseCategory> findAllByCompany_IdAndDeletedFalseOrderBySortOrderAscNameAsc(Long companyId);

    Optional<ExpenseCategory> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    long countByCompany_IdAndDeletedFalse(Long companyId);

    boolean existsByCompany_IdAndCodeAndDeletedFalse(Long companyId, String code);
}
