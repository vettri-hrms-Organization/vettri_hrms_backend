package com.haodaone.software.repository;

import com.haodaone.software.entity.SoftwarePackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SoftwarePackageRepository extends JpaRepository<SoftwarePackage, Long> {
    List<SoftwarePackage> findByCompany_IdAndDeletedFalseOrderByNameAsc(Long companyId);
    Optional<SoftwarePackage> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
    Optional<SoftwarePackage> findByIdAndDeletedFalse(Long id);
}
