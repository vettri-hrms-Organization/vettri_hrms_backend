package com.haodaone.helpdesk;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/** A reply on a helpdesk ticket (V67); internal notes are hidden from the employee. */
@Entity
@Table(name = "helpdesk_comment")
public class HelpdeskComment extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "author_employee_id")
    private Long authorEmployeeId;

    @Column(name = "author_name", length = 150)
    private String authorName;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "internal", nullable = false)
    private boolean internal;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public Long getAuthorEmployeeId() { return authorEmployeeId; }
    public void setAuthorEmployeeId(Long authorEmployeeId) { this.authorEmployeeId = authorEmployeeId; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isInternal() { return internal; }
    public void setInternal(boolean internal) { this.internal = internal; }
}
