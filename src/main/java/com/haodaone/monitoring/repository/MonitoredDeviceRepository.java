package com.haodaone.monitoring.repository;

import com.haodaone.monitoring.entity.MonitoredDevice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.Optional;

public interface MonitoredDeviceRepository extends JpaRepository<MonitoredDevice, Long> {

    List<MonitoredDevice> findAllByDeletedFalseOrderByDeviceNameAsc();

    List<MonitoredDevice> findAllByCompany_IdAndDeletedFalseOrderByDeviceNameAsc(Long companyId);

    @Query("select d from MonitoredDevice d where d.company.id = :companyId and d.deleted = false and d.employee.id in :employeeIds order by d.deviceName asc")
    List<MonitoredDevice> findAllScoped(@Param("companyId") Long companyId, @Param("employeeIds") Set<Long> employeeIds);

    Optional<MonitoredDevice> findByIdAndDeletedFalse(Long id);

    Optional<MonitoredDevice> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    boolean existsByIdAndDeletedFalse(Long id);

    Optional<MonitoredDevice> findByDeviceIdAndDeletedFalse(String deviceId);

    /** Used by the agent-token authentication filter - looked up by hash, never by raw token (see security.AgentTokenAuthenticationFilter). */
    @EntityGraph(attributePaths = "company")
    Optional<MonitoredDevice> findByAgentTokenHashAndDeletedFalse(String agentTokenHash);

    boolean existsByAgentTokenHash(String agentTokenHash);

    long countByCompany_IdAndDeletedFalse(Long companyId);
}
