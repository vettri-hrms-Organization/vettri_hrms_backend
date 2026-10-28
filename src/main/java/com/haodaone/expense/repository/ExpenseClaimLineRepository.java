package com.haodaone.expense.repository;

import com.haodaone.expense.entity.ExpenseClaimLine;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ExpenseClaimLineRepository extends JpaRepository<ExpenseClaimLine, Long> {

    @EntityGraph(attributePaths = {"category"})
    List<ExpenseClaimLine> findAllByClaim_IdAndDeletedFalseOrderByExpenseDateAscIdAsc(Long claimId);

    @EntityGraph(attributePaths = {"category"})
    List<ExpenseClaimLine> findAllByClaim_IdInAndDeletedFalseOrderByExpenseDateAscIdAsc(Collection<Long> claimIds);
}
