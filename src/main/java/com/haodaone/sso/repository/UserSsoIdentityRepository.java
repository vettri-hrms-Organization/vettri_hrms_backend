package com.haodaone.sso.repository;

import com.haodaone.sso.entity.UserSsoIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSsoIdentityRepository extends JpaRepository<UserSsoIdentity, Long> {

    Optional<UserSsoIdentity> findByProviderAndSubjectAndDeletedFalse(String provider, String subject);

    List<UserSsoIdentity> findAllByUser_IdAndProviderAndDeletedFalse(Long userId, String provider);
}
