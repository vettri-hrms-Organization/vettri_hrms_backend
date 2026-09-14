package com.haodaone.user.repository;

import com.haodaone.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameAndDeletedFalse(String username);

    Optional<User> findByEmailAndDeletedFalse(String email);

    Optional<User> findByEmailIgnoreCaseAndDeletedFalse(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    @Query(value = "select pg_advisory_xact_lock(:employeeId)", nativeQuery = true)
    void lockEmployeeForCurrentTransaction(@Param("employeeId") Long employeeId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    java.util.List<User> findAllByDeletedFalse();

    java.util.List<User> findAllByCompanyIdAndDeletedFalse(Long companyId);

    Optional<User> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
