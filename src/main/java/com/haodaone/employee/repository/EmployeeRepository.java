package com.haodaone.employee.repository;

import com.haodaone.employee.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findAllByDeletedFalseOrderByFirstNameAsc();

        List<Employee> findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(Long companyId);

    List<Employee> findAllByReportingManagerIdAndDeletedFalse(Long managerId);

    List<Employee> findAllByDepartmentIdAndDeletedFalse(Long departmentId);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    /** Used by monitoring.AgentIngestService#resolveEmployee - the agent authenticates by Employee ID first, windowsUsername only as a fallback. */
    Optional<Employee> findByEmployeeCodeAndDeletedFalse(String employeeCode);

        Optional<Employee> findByEmployeeCodeIgnoreCaseAndDeletedFalse(String employeeCode);

    Optional<Employee> findByBiometricDeviceUserIdAndDeletedFalse(String biometricDeviceUserId);

    Optional<Employee> findByUser_UsernameAndDeletedFalse(String username);

        Optional<Employee> findByUser_IdAndDeletedFalse(Long userId);

    boolean existsByEmail(String email);

    boolean existsByEmployeeCode(String employeeCode);

    long countByDeletedFalse();

    long countByCompany_IdAndDeletedFalse(Long companyId);

    long countByStatusAndDeletedFalse(String status);

    long countByCompany_IdAndStatusAndDeletedFalse(Long companyId, String status);

    long countByCompany_IdAndStatusInAndDeletedFalse(Long companyId, java.util.Collection<String> statuses);

    long countByDepartmentIdAndDeletedFalse(Long departmentId);

    long countByTeamIdAndDeletedFalse(Long teamId);

    long countByEmploymentTypeAndDeletedFalse(String employmentType);

    long countByCompany_IdAndEmploymentTypeAndDeletedFalse(Long companyId, String employmentType);

    long countByDateOfJoiningGreaterThanEqualAndDeletedFalse(LocalDate since);

    long countByCompany_IdAndDateOfJoiningGreaterThanEqualAndDeletedFalse(Long companyId, LocalDate since);

    @Query("select count(e) from Employee e where e.deleted = false " +
            "and e.company.id = :companyId " +
            "and e.status in ('Resigned','Terminated','Exit Clearance','Assets Returned') and e.updatedAt >= :since")
    long countSeparationsSinceForCompany(@Param("companyId") Long companyId, @Param("since") LocalDateTime since);

    @Query("select count(e) from Employee e where e.deleted = false " +
            "and e.status in ('Resigned','Terminated','Exit Clearance','Assets Returned') and e.updatedAt >= :since")
    long countSeparationsSince(@Param("since") LocalDateTime since);

    @Query("select e from Employee e where e.deleted = false and (" +
            "lower(e.firstName) like lower(concat('%', :term, '%')) or " +
            "lower(e.lastName) like lower(concat('%', :term, '%')) or " +
            "lower(e.employeeCode) like lower(concat('%', :term, '%')) or " +
            "lower(e.email) like lower(concat('%', :term, '%')))")
    List<Employee> search(@Param("term") String term);

    /**
     * Paginated versions of findAllByDeletedFalseOrderByFirstNameAsc / search,
     * used by the Employee Directory (EmployeeController#listAll) now that
     * it's actually paged - see Phase 1 audit ("no pagination visible,
     * problem at 5,000 employees"). The unpaginated originals above are
     * left untouched since Dashboard/my-team/recentJoiners etc. all rely
     * on their existing bounded/unpaginated behavior and don't need this.
     */
        @EntityGraph(attributePaths = {"department", "designation", "reportingManager"})
        Page<Employee> findAllByDeletedFalse(Pageable pageable);

    @Query("select e from Employee e where e.deleted = false and (" +
            "lower(e.firstName) like lower(concat('%', :term, '%')) or " +
            "lower(e.lastName) like lower(concat('%', :term, '%')) or " +
            "lower(e.employeeCode) like lower(concat('%', :term, '%')) or " +
            "lower(e.email) like lower(concat('%', :term, '%')))")
        @EntityGraph(attributePaths = {"department", "designation", "reportingManager"})
        Page<Employee> searchPaged(@Param("term") String term, Pageable pageable);

    /**
     * Paged directory filtered to one department - powers the drill-down
     * from Reports' "Headcount by Department" bars straight into a
     * pre-filtered Employee Directory. Deliberately a separate method
     * rather than adding an optional departmentId param to searchPaged
     * above: that method's `term` is required (empty string, not null,
     * when unused - see EmployeeService#listPaged), so bolting an
     * optional filter onto it would mean two different null-handling
     * conventions in one query. searchForPayroll shows the same optional-
     * param pattern already if this ever needs to merge with it.
     */
    @Query("select e from Employee e where e.deleted = false and e.department.id = :departmentId and (" +
            ":term = '' or " +
            "lower(e.firstName) like lower(concat('%', :term, '%')) or " +
            "lower(e.lastName) like lower(concat('%', :term, '%')) or " +
            "lower(e.employeeCode) like lower(concat('%', :term, '%')) or " +
            "lower(e.email) like lower(concat('%', :term, '%')))")
        @EntityGraph(attributePaths = {"department", "designation", "reportingManager"})
        Page<Employee> searchPagedByDepartment(@Param("term") String term, @Param("departmentId") Long departmentId, Pageable pageable);

    /**
     * Filterable roster used by the Salary module's Employee Salary List
     * (search + department + status, all optional). Kept here rather than
     * duplicated in the salary package since Employee filtering belongs
     * next to the rest of the Employee query surface - the salary module
     * only adds the amounts on top.
     */
        @Query("select e from Employee e where e.deleted = false and e.company.id = :companyId " +
            "and (:departmentId is null or e.department.id = :departmentId) " +
            "and (:status is null or e.status = :status) " +
            "and (:term is null or " +
            "lower(e.firstName) like lower(concat('%', :term, '%')) or " +
            "lower(e.lastName) like lower(concat('%', :term, '%')) or " +
            "lower(e.employeeCode) like lower(concat('%', :term, '%')) or " +
            "lower(e.email) like lower(concat('%', :term, '%')))")
        List<Employee> searchForPayroll(@Param("companyId") Long companyId, @Param("term") String term,
                                                                        @Param("departmentId") Long departmentId, @Param("status") String status);

                Optional<Employee> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

        Optional<Employee> findByEmailIgnoreCaseAndCompany_IdAndDeletedFalse(String email, Long companyId);

        Optional<Employee> findByEmployeeCodeIgnoreCaseAndCompany_IdAndDeletedFalse(String employeeCode, Long companyId);

        Optional<Employee> findByEmailIgnoreCaseAndCompany_IdAndDeletedFalseAndIdNot(String email, Long companyId, Long id);

    List<Employee> findTop5ByDeletedFalseOrderByDateOfJoiningDesc();

    List<Employee> findTop5ByCompany_IdAndDeletedFalseOrderByDateOfJoiningDesc(Long companyId);

    Page<Employee> findAllByCompany_IdAndDeletedFalse(Long companyId, Pageable pageable);

    @Query("select e from Employee e where e.deleted = false and e.company.id = :companyId and (" +
            "lower(e.firstName) like lower(concat('%', :term, '%')) or " +
            "lower(e.lastName) like lower(concat('%', :term, '%')) or " +
            "lower(e.employeeCode) like lower(concat('%', :term, '%')) or " +
            "lower(e.email) like lower(concat('%', :term, '%')))" )
    @EntityGraph(attributePaths = {"department", "designation", "reportingManager"})
    Page<Employee> searchPagedForCompany(@Param("companyId") Long companyId, @Param("term") String term, Pageable pageable);

    @Query("select e from Employee e where e.deleted = false and e.company.id = :companyId and e.department.id = :departmentId and (" +
            ":term = '' or " +
            "lower(e.firstName) like lower(concat('%', :term, '%')) or " +
            "lower(e.lastName) like lower(concat('%', :term, '%')) or " +
            "lower(e.employeeCode) like lower(concat('%', :term, '%')) or " +
            "lower(e.email) like lower(concat('%', :term, '%')))" )
    @EntityGraph(attributePaths = {"department", "designation", "reportingManager"})
    Page<Employee> searchPagedByDepartmentForCompany(@Param("companyId") Long companyId, @Param("term") String term, @Param("departmentId") Long departmentId, Pageable pageable);

    /** Highest numeric suffix currently in use for employee codes with the given prefix - used to generate the next code. */
    @Query(value = "select coalesce(max(cast(substring(e.employee_id, :prefixLength + 1) as integer)), 0) " +
            "from employee e where e.employee_id like concat(:prefix, '%')", nativeQuery = true)
    Integer findMaxEmployeeCodeSuffix(@Param("prefix") String prefix, @Param("prefixLength") int prefixLength);
}
