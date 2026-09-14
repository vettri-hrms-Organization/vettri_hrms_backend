package com.haodaone.remotesupport.repository;
import com.haodaone.remotesupport.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RemoteSupportJobRepository extends JpaRepository<RemoteSupportJob, Long> {
    List<RemoteSupportJob> findByDevice_IdAndStatusAndDeletedFalseOrderByCreatedAtAsc(Long deviceId, RemoteSupportStatus status);
    List<RemoteSupportJob> findByDevice_IdAndStatusInAndDeletedFalseOrderByCreatedAtAsc(Long deviceId, List<RemoteSupportStatus> statuses);
    List<RemoteSupportJob> findByCompany_IdAndDevice_IdAndDeletedFalseOrderByCreatedAtDesc(Long companyId, Long deviceId);
    Optional<RemoteSupportJob> findByIdAndCompany_IdAndDevice_IdAndDeletedFalse(Long id, Long companyId, Long deviceId);
    Optional<RemoteSupportJob> findByIdAndDevice_IdAndDeletedFalse(Long id, Long deviceId);
}