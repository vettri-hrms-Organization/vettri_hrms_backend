package com.haodaone.onboarding.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.user.entity.User;
import jakarta.persistence.*;

/** One task line of a checklist template. dueOffsetDays is relative to the anchor date (joining date / last working date). */
@Entity
@Table(name = "checklist_template_task")
public class ChecklistTemplateTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ChecklistTemplate template;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    /** NEW_HIRE, HR, MANAGER, IT or USER (see ChecklistConstants). */
    @Column(name = "owner_type", nullable = false, length = 20)
    private String ownerType;

    /** Specific owner for USER tasks (optional override for IT tasks). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User ownerUser;

    @Column(name = "due_offset_days", nullable = false)
    private int dueOffsetDays;

    @Column(name = "requires_document", nullable = false)
    private boolean requiresDocument;

    @Column(name = "document_type", length = 40)
    private String documentType;

    /** Optional UI deep link, e.g. BANK_DETAILS, APPOINTMENT_LETTER, RELIEVING_LETTER. */
    @Column(name = "link_key", length = 40)
    private String linkKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public ChecklistTemplate getTemplate() { return template; }
    public void setTemplate(ChecklistTemplate template) { this.template = template; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }
    public User getOwnerUser() { return ownerUser; }
    public void setOwnerUser(User ownerUser) { this.ownerUser = ownerUser; }
    public int getDueOffsetDays() { return dueOffsetDays; }
    public void setDueOffsetDays(int dueOffsetDays) { this.dueOffsetDays = dueOffsetDays; }
    public boolean isRequiresDocument() { return requiresDocument; }
    public void setRequiresDocument(boolean requiresDocument) { this.requiresDocument = requiresDocument; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getLinkKey() { return linkKey; }
    public void setLinkKey(String linkKey) { this.linkKey = linkKey; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
