package com.haodaone.employee.repository;

import com.haodaone.employee.entity.EmployeeInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface EmployeeInvitationRepository extends JpaRepository<EmployeeInvitation, Long> {
    @Query("select i from EmployeeInvitation i join fetch i.employee where i.tokenHash = :tokenHash")
    Optional<EmployeeInvitation> findByTokenHashWithEmployee(@Param("tokenHash") String tokenHash);

    @Query("select i from EmployeeInvitation i join fetch i.employee where i.tokenHash = :tokenHash")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmployeeInvitation> findByTokenHashWithEmployeeForUpdate(@Param("tokenHash") String tokenHash);

    Optional<EmployeeInvitation> findByEmployee_IdAndStatus(Long employeeId, String status);
}