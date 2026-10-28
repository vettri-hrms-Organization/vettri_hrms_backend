package com.haodaone.announcement.repository;

import com.haodaone.announcement.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Optional<Announcement> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    Optional<Announcement> findByIdAndDeletedFalse(Long id);

    List<Announcement> findAllByCompany_IdAndDeletedFalseOrderByIdDesc(Long companyId);

    List<Announcement> findAllByCompany_IdAndStatusAndDeletedFalse(Long companyId, String status);

    @Query("select a.id from Announcement a where a.deleted = false and a.status = 'SCHEDULED' and a.publishAt <= :now")
    List<Long> findDueScheduledIds(@Param("now") LocalDateTime now);

    @Query("select a.id from Announcement a where a.deleted = false and a.status = 'PUBLISHED' and a.notifiedAt is null and a.publishedAt <= :before")
    List<Long> findPublishedUnnotifiedIds(@Param("before") LocalDateTime before);

    /** Atomic SCHEDULED -> PUBLISHED transition; returns 0 when another worker already did it. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Announcement a set a.status = 'PUBLISHED', a.publishedAt = :now, a.updatedAt = :now "
            + "where a.id = :id and a.status = 'SCHEDULED' and a.deleted = false")
    int publishIfScheduled(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Claims the one-time audience notification; returns 0 when it was already claimed. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Announcement a set a.notifiedAt = :now "
            + "where a.id = :id and a.notifiedAt is null and a.status = 'PUBLISHED' and a.deleted = false")
    int claimNotification(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * Rows of [employeeId, userId, departmentId (nullable)] for every employee
     * with an active login - one query resolves any audience.
     */
    @Query("select e.id, u.id, d.id from Employee e join e.user u left join e.department d "
            + "where e.company.id = :companyId and e.deleted = false and u.active = true "
            + "and (e.status is null or e.status not in :excludedStatuses)")
    List<Object[]> findAudienceMembers(@Param("companyId") Long companyId,
                                       @Param("excludedStatuses") Collection<String> excludedStatuses);
}
