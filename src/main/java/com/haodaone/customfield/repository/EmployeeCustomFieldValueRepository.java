package com.haodaone.customfield.repository;

import com.haodaone.customfield.entity.EmployeeCustomFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EmployeeCustomFieldValueRepository extends JpaRepository<EmployeeCustomFieldValue, Long> {

    List<EmployeeCustomFieldValue> findAllByCompany_IdAndEmployee_IdAndDeletedFalse(Long companyId, Long employeeId);

    List<EmployeeCustomFieldValue> findAllByCompany_IdAndDeletedFalse(Long companyId);

    List<EmployeeCustomFieldValue> findAllByCompany_IdAndEmployee_IdInAndDeletedFalse(Long companyId, Collection<Long> employeeIds);

    List<EmployeeCustomFieldValue> findAllByDefinition_IdAndDeletedFalse(Long definitionId);
}
