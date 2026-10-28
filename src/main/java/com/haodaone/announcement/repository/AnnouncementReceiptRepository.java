package com.haodaone.announcement.repository;

import com.haodaone.announcement.entity.AnnouncementReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnnouncementReceiptRepository extends JpaRepository<AnnouncementReceipt, Long> {

    Optional<AnnouncementReceipt> findByAnnouncement_IdAndEmployee_IdAndDeletedFalse(Long announcementId, Long employeeId);

    List<AnnouncementReceipt> findAllByCompany_IdAndEmployee_IdAndDeletedFalse(Long companyId, Long employeeId);

    List<AnnouncementReceipt> findAllByAnnouncement_IdAndDeletedFalse(Long announcementId);

    /** Rows of [announcementId, readCount, acknowledgedCount] for one company. */
    @Query("select r.announcement.id, count(r.readAt), count(r.acknowledgedAt) from AnnouncementReceipt r "
            + "where r.company.id = :companyId and r.deleted = false group by r.announcement.id")
    List<Object[]> countsByAnnouncement(@Param("companyId") Long companyId);
}
