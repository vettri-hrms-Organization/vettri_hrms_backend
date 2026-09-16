package com.haodaone.attendance.repository;

import com.haodaone.attendance.entity.OfficeLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfficeLocationRepository extends JpaRepository<OfficeLocation, Long> {

    List<OfficeLocation> findAllByCompany_IdAndActiveTrueAndDeletedFalseOrderByNameAsc(Long companyId);

    Optional<OfficeLocation> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    List<OfficeLocation> findAllByCompany_IdAndDeletedFalseOrderByNameAsc(Long companyId);
}
