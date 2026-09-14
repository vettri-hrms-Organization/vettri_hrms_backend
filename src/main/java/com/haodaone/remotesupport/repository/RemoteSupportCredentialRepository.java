package com.haodaone.remotesupport.repository;
import com.haodaone.remotesupport.entity.RemoteSupportCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface RemoteSupportCredentialRepository extends JpaRepository<RemoteSupportCredential, Long> { Optional<RemoteSupportCredential> findByDeviceId(Long deviceId); }