package com.haodaone.attendance.repository;

import com.haodaone.attendance.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findAllByCompany_IdAndPunchTimeBetweenOrderByPunchTimeDesc(Long companyId, LocalDateTime start, LocalDateTime end);

    List<AttendanceRecord> findAllByEmployeeIdOrderByPunchTimeDesc(Long employeeId);

    List<AttendanceRecord> findAllByCompany_IdAndEmployee_IdOrderByPunchTimeDesc(Long companyId, Long employeeId);

    List<AttendanceRecord> findAllByCompany_IdAndEmployeeIsNullOrderByPunchTimeDesc(Long companyId);

    Optional<AttendanceRecord> findByDeviceSerialNumberAndDeviceUserIdAndPunchTime(
            String deviceSerialNumber, String deviceUserId, LocalDateTime punchTime);

    long countByDeviceUserIdAndPunchTimeBetween(String deviceUserId, LocalDateTime start, LocalDateTime end);

    @Query("select count(distinct a.employee.id) from AttendanceRecord a where a.company.id = :companyId and a.employee is not null and a.punchTime between :start and :end")
    long countDistinctEmployeesPunchedBetweenForCompany(@Param("companyId") Long companyId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countByCompany_IdAndPunchTimeBetween(Long companyId, LocalDateTime start, LocalDateTime end);

    @Query("select a.employee.department.name, count(a) from AttendanceRecord a " +
            "where a.company.id = :companyId and a.employee is not null and a.employee.department is not null and a.punchTime between :start and :end " +
            "group by a.employee.department.name")
    List<Object[]> countByDepartmentBetweenForCompany(@Param("companyId") Long companyId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
