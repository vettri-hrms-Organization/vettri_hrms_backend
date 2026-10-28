package com.haodaone.expense.repository;

import com.haodaone.expense.entity.ExpenseClaim;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, Long> {

    @EntityGraph(attributePaths = {"employee", "employee.department", "employee.reportingManager"})
    Optional<ExpenseClaim> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    @EntityGraph(attributePaths = {"employee", "employee.department"})
    List<ExpenseClaim> findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByIdDesc(Long companyId, Long employeeId);

    @EntityGraph(attributePaths = {"employee", "employee.department", "employee.reportingManager"})
    List<ExpenseClaim> findAllByCompany_IdAndStatusInAndDeletedFalseOrderByIdAsc(Long companyId, Collection<String> statuses);

    @EntityGraph(attributePaths = {"employee", "employee.department"})
    List<ExpenseClaim> findAllByCompany_IdAndDeletedFalseOrderByIdDesc(Long companyId);

    @EntityGraph(attributePaths = {"employee"})
    List<ExpenseClaim> findAllByCompany_IdAndEmployee_IdAndStatusAndReimbursedAtIsNullAndPayrollItemIdIsNullAndDeletedFalseOrderByIdAsc(
            Long companyId, Long employeeId, String status);

    @EntityGraph(attributePaths = {"employee"})
    List<ExpenseClaim> findAllByCompany_IdAndIdInAndDeletedFalse(Long companyId, Collection<Long> ids);
}
