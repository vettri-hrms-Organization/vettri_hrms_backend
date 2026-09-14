package com.haodaone.remotecommand.repository;

import com.haodaone.remotecommand.entity.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface RemoteCommandJobRepository extends JpaRepository<RemoteCommandJob, Long> {
    List<RemoteCommandJob> findByCompany_IdAndDevice_IdAndDeletedFalseOrderByCreatedAtDesc(Long companyId, Long deviceId);
    Optional<RemoteCommandJob> findByIdAndCompany_IdAndDevice_IdAndDeletedFalse(Long id, Long companyId, Long deviceId);
    Optional<RemoteCommandJob> findByIdAndDevice_IdAndDeletedFalse(Long id, Long deviceId);
    List<RemoteCommandJob> findByDevice_IdAndStatusAndDeletedFalseOrderByCreatedAtAsc(Long deviceId, RemoteCommandStatus status);
}