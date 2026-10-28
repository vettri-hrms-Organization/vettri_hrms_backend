package com.haodaone.leave.repository;

import com.haodaone.leave.entity.OptionalHolidaySelection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OptionalHolidaySelectionRepository extends JpaRepository<OptionalHolidaySelection, Long> {

    List<OptionalHolidaySelection> findAllByCompany_IdAndEmployee_IdAndHolidayYearAndDeletedFalse(Long companyId, Long employeeId, int holidayYear);

    long countByCompany_IdAndEmployee_IdAndHolidayYearAndDeletedFalse(Long companyId, Long employeeId, int holidayYear);

    Optional<OptionalHolidaySelection> findFirstByCompany_IdAndEmployee_IdAndHoliday_IdAndDeletedFalse(Long companyId, Long employeeId, Long holidayId);

    /** Picked holiday ids per employee for a set of holidays: rows of [employeeId, holidayId]. */
    @Query("""
            select s.employee.id, s.holiday.id
            from OptionalHolidaySelection s
            where s.company.id = :companyId
              and s.deleted = false
              and s.employee.id in :employeeIds
              and s.holiday.id in :holidayIds
            """)
    List<Object[]> findPickedPairs(@Param("companyId") Long companyId,
                                   @Param("employeeIds") Collection<Long> employeeIds,
                                   @Param("holidayIds") Collection<Long> holidayIds);
}
