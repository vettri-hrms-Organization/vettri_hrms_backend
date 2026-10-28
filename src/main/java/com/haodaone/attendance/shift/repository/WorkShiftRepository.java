package com.haodaone.attendance.shift.repository;

import com.haodaone.attendance.shift.entity.WorkShift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {

    List<WorkShift> findAllByCompany_IdAndDeletedFalseOrderByStartTimeAscNameAsc(Long companyId);

    Optional<WorkShift> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    Optional<WorkShift> findFirstByCompany_IdAndCompanyDefaultTrueAndDeletedFalse(Long companyId);

    boolean existsByCompany_IdAndCodeIgnoreCaseAndDeletedFalse(Long companyId, String code);

    boolean existsByCompany_IdAndCodeIgnoreCaseAndDeletedFalseAndIdNot(Long companyId, String code, Long id);
}
