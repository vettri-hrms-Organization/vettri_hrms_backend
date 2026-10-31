package com.haodaone.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HelpdeskTicketRepository extends JpaRepository<HelpdeskTicket, Long> {
    List<HelpdeskTicket> findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(Long companyId);
    List<HelpdeskTicket> findAllByCompanyIdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(Long companyId, Long employeeId);
    Optional<HelpdeskTicket> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
    long countByCompanyId(Long companyId);
    List<HelpdeskTicket> findAllByStatusAndResolvedAtBeforeAndDeletedFalse(String status, java.time.LocalDateTime before);
}
