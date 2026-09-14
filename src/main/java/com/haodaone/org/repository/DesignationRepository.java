package com.haodaone.org.repository;

import com.haodaone.org.entity.Designation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DesignationRepository extends JpaRepository<Designation, Long> {
    List<Designation> findAllByDeletedFalseOrderByTitleAsc();
}
