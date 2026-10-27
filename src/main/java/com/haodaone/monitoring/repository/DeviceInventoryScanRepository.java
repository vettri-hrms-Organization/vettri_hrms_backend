package com.haodaone.monitoring.repository;

import com.haodaone.monitoring.entity.DeviceInventoryScan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceInventoryScanRepository extends JpaRepository<DeviceInventoryScan, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DeviceInventoryScan s where s.device.id = :deviceId and s.status = 'PENDING' and s.deleted = false order by s.requestedAt")
    List<DeviceInventoryScan> findPendingForDeviceForUpdate(@Param("deviceId") Long deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DeviceInventoryScan s where s.requestId = :requestId and s.deleted = false")
    Optional<DeviceInventoryScan> findByRequestIdForUpdate(@Param("requestId") UUID requestId);

    Optional<DeviceInventoryScan> findFirstByDevice_IdAndDeletedFalseOrderByRequestedAtDesc(Long deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "device")
    @Query("select s from DeviceInventoryScan s where s.status in ('PENDING', 'SCANNING') and s.expiresAt <= :now and s.deleted = false")
    List<DeviceInventoryScan> findExpiredForUpdate(@Param("now") java.time.LocalDateTime now);
}
