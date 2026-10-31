package com.haodaone.esign;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/** An employee's saved signature picture (V69), stamped on every document they sign. One live row per employee. */
@Entity
@Table(name = "employee_signature")
public class EmployeeSignature extends BaseEntity {

    public static final String UPLOAD = "UPLOAD";
    public static final String DRAWN = "DRAWN";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "s3_object_key", nullable = false, length = 500)
    private String s3ObjectKey;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Column(name = "source", nullable = false, length = 10)
    private String source;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getS3ObjectKey() { return s3ObjectKey; }
    public void setS3ObjectKey(String s3ObjectKey) { this.s3ObjectKey = s3ObjectKey; }
    public String getContentSha256() { return contentSha256; }
    public void setContentSha256(String contentSha256) { this.contentSha256 = contentSha256; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
