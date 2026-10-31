package com.haodaone.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HelpdeskCommentRepository extends JpaRepository<HelpdeskComment, Long> {
    List<HelpdeskComment> findAllByTicketIdAndDeletedFalseOrderByCreatedAtAsc(Long ticketId);
}
