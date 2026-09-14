package com.haodaone.software.repository;

import com.haodaone.software.entity.SoftwareVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SoftwareVersionRepository extends JpaRepository<SoftwareVersion, Long> {
    List<SoftwareVersion> findBySoftwarePackage_IdAndDeletedFalseOrderByVersionDesc(Long packageId);
    Optional<SoftwareVersion> findByIdAndDeletedFalse(Long id);
    Optional<SoftwareVersion> findByIdAndSoftwarePackage_Company_IdAndDeletedFalse(Long id, Long companyId);
}
