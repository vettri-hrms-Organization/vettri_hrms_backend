package com.haodaone.attendance.repository;

import com.haodaone.attendance.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findBySerialNumber(String serialNumber);
    Optional<Device> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
    /** Tenant-scoped: only company devices. Global queries not allowed for security. */
    List<Device> findAllByCompany_IdAndDeletedFalseOrderByDeviceNameAsc(Long companyId);
}
