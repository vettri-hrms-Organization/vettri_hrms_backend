package com.haodaone.letters.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

/**
 * A company's letter template (offer, appointment, relieving, ...). The body
 * is plain text with {{placeholder}} tokens resolved by LetterRenderer at
 * generation time; built-in templates are seeded per company on first use.
 */
@Entity
@Table(name = "letter_template")
public class LetterTemplate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** See LetterType constants. */
    @Column(name = "letter_type", nullable = false, length = 40)
    private String letterType;

    @Column(nullable = false, length = 150)
    private String name;

    /** Heading printed on the letter, e.g. "Appointment Letter". */
    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @Column(nullable = false)
    private boolean active = true;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getLetterType() { return letterType; }
    public void setLetterType(String letterType) { this.letterType = letterType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
