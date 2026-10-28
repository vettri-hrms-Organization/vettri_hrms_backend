package com.haodaone.org.repository;

import com.haodaone.org.entity.Designation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DesignationRepository extends JpaRepository<Designation, Long> {
    List<Designation> findAllByCompany_IdAndDeletedFalseOrderByTitleAsc(Long companyId);

    Optional<Designation> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
}
