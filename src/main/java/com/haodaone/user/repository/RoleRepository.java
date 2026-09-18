package com.haodaone.user.repository;

import com.haodaone.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    Optional<Role> findByNameAndCompany_Id(String name, Long companyId);

    Optional<Role> findByIdAndDeletedFalseAndCompany_Id(Long id, Long companyId);

    Optional<Role> findByIdAndDeletedFalseAndCompany_IdIsNull(Long id);

    List<Role> findAllByDeletedFalse();

    @Query("select r from Role r where r.deleted = false and (r.company.id = :companyId or r.company is null)")
    List<Role> findAvailableForCompany(@Param("companyId") Long companyId);
}
