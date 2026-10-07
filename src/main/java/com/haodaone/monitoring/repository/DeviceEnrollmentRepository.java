package com.haodaone.monitoring.repository;

import com.haodaone.monitoring.entity.DeviceEnrollment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceEnrollmentRepository extends JpaRepository<DeviceEnrollment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"company", "employee", "device"})
    @Query("select enrollment from DeviceEnrollment enrollment where enrollment.tokenHash = :tokenHash and enrollment.deleted = false")
    Optional<DeviceEnrollment> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @EntityGraph(attributePaths = {"company", "employee", "device"})
    Optional<DeviceEnrollment> findByTokenHashAndDeletedFalse(String tokenHash);

    @EntityGraph(attributePaths = {"company", "employee", "device"})
    List<DeviceEnrollment> findAllByCompany_IdAndDeletedFalseOrderByCreatedAtDesc(Long companyId);

    @EntityGraph(attributePaths = {"company", "employee", "device"})
    Optional<DeviceEnrollment> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
}
