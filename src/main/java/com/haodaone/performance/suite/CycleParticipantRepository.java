package com.haodaone.performance.suite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CycleParticipantRepository extends JpaRepository<CycleParticipant, Long> {
    List<CycleParticipant> findAllByCycleIdAndDeletedFalseOrderByIdAsc(Long cycleId);
    List<CycleParticipant> findAllByEmployeeIdAndDeletedFalseOrderByIdDesc(Long employeeId);
    Optional<CycleParticipant> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
