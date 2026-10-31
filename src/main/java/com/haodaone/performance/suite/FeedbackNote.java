package com.haodaone.performance.suite;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/** Continuous feedback (V66). PUBLIC praise shows on the kudos wall; PRIVATE is for the recipient, sender, recipient's manager and HR. */
@Entity
@Table(name = "feedback_note")
public class FeedbackNote extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "from_employee_id", nullable = false)
    private Long fromEmployeeId;

    @Column(name = "to_employee_id", nullable = false)
    private Long toEmployeeId;

    @Column(name = "kind", nullable = false, length = 12)
    private String kind;

    @Column(name = "visibility", nullable = false, length = 10)
    private String visibility;

    @Column(name = "value_tag", length = 40)
    private String valueTag;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getFromEmployeeId() { return fromEmployeeId; }
    public void setFromEmployeeId(Long fromEmployeeId) { this.fromEmployeeId = fromEmployeeId; }
    public Long getToEmployeeId() { return toEmployeeId; }
    public void setToEmployeeId(Long toEmployeeId) { this.toEmployeeId = toEmployeeId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getValueTag() { return valueTag; }
    public void setValueTag(String valueTag) { this.valueTag = valueTag; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
