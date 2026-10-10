package com.haodaone.user.repository;

import com.haodaone.user.entity.UserPermissionGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPermissionGrantRepository extends JpaRepository<UserPermissionGrant, Long> {

    List<UserPermissionGrant> findAllByCompany_IdAndUser_IdAndDeletedFalseOrderByGrantedAtDesc(
            Long companyId, Long userId);

    List<UserPermissionGrant> findAllByCompany_IdAndUser_IdAndRevokedAtIsNullAndDeletedFalse(
            Long companyId, Long userId);

    List<UserPermissionGrant> findAllByCompany_IdAndRevokedAtIsNullAndDeletedFalse(Long companyId);

    Optional<UserPermissionGrant> findByIdAndCompany_IdAndUser_IdAndDeletedFalse(
            Long id, Long companyId, Long userId);

    boolean existsByCompany_IdAndUser_IdAndPermission_CodeAndRevokedAtIsNullAndDeletedFalse(
            Long companyId, Long userId, String permissionCode);
}
