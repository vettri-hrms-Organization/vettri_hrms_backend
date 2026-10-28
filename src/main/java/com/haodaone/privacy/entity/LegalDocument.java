package com.haodaone.privacy.entity;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Platform-wide registry of published legal document versions. Not
 * company-scoped: Vettri's own Terms/Privacy/DPA apply to every tenant.
 * Rows are synced from app.legal.* configuration at startup
 * (see LegalDocumentService#syncRegistry); the config is the source of truth
 * for the current version, this table keeps the history.
 */
@Entity
@Table(name = "legal_document",
        uniqueConstraints = @UniqueConstraint(name = "uk_legal_document_type_version", columnNames = {"doc_type", "doc_version"}))
public class LegalDocument extends BaseEntity {

    @Column(name = "doc_type", nullable = false, length = 30)
    private String docType;

    @Column(name = "doc_version", nullable = false, length = 40)
    private String docVersion;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "is_current", nullable = false)
    private boolean current = true;

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getDocVersion() { return docVersion; }
    public void setDocVersion(String docVersion) { this.docVersion = docVersion; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public boolean isCurrent() { return current; }
    public void setCurrent(boolean current) { this.current = current; }
}
