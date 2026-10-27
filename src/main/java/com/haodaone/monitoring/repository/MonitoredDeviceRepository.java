package com.haodaone.monitoring.repository;

import com.haodaone.monitoring.entity.MonitoredDevice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.Optional;

public interface MonitoredDeviceRepository extends JpaRepository<MonitoredDevice, Long> {

    List<MonitoredDevice> findAllByDeletedFalseOrderByDeviceNameAsc();

    List<MonitoredDevice> findAllByCompany_IdAndDeletedFalseOrderByDeviceNameAsc(Long companyId);

    List<MonitoredDevice> findAllByCompany_IdAndEmployee_IdAndActiveTrueAndDeletedFalseOrderByDeviceNameAsc(
            Long companyId, Long employeeId);

    Optional<MonitoredDevice> findByIdAndCompany_IdAndEmployee_IdAndActiveTrueAndDeletedFalse(
            Long id, Long companyId, Long employeeId);

    @Query("select d from MonitoredDevice d where d.company.id = :companyId and d.deleted = false and d.employee.id in :employeeIds order by d.deviceName asc")
    List<MonitoredDevice> findAllScoped(@Param("companyId") Long companyId, @Param("employeeIds") Set<Long> employeeIds);

    Optional<MonitoredDevice> findByIdAndDeletedFalse(Long id);

    Optional<MonitoredDevice> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from MonitoredDevice d where d.id = :id and d.company.id = :companyId and d.deleted = false")
    Optional<MonitoredDevice> findByIdAndCompany_IdAndDeletedFalseForUpdate(
            @Param("id") Long id, @Param("companyId") Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from MonitoredDevice d where d.id = :id and d.deleted = false")
    Optional<MonitoredDevice> findByIdAndDeletedFalseForUpdate(@Param("id") Long id);

    boolean existsByIdAndDeletedFalse(Long id);

    Optional<MonitoredDevice> findByDeviceIdAndDeletedFalse(String deviceId);

    /** Used by the agent-token authentication filter - looked up by hash, never by raw token (see security.AgentTokenAuthenticationFilter). */
    @EntityGraph(attributePaths = "company")
    Optional<MonitoredDevice> findByAgentTokenHashAndDeletedFalse(String agentTokenHash);

    boolean existsByAgentTokenHash(String agentTokenHash);

    long countByCompany_IdAndDeletedFalse(Long companyId);
}
