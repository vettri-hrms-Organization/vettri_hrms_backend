package com.haodaone.remotesupport.entity;

import com.haodaone.company.entity.Company;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "remote_support_credential")
public class RemoteSupportCredential {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id", nullable = false) private Company company;
    @Column(name = "device_id", nullable = false, unique = true) private Long deviceId;
    @Column(name = "encrypted_secret", nullable = false, columnDefinition = "TEXT") private String encryptedSecret;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RemoteSupportStatus status;
    @Column(name = "rotated_at", nullable = false) private LocalDateTime rotatedAt;
    public Long getId(){return id;} public Company getCompany(){return company;} public void setCompany(Company v){company=v;}
    public Long getDeviceId(){return deviceId;} public void setDeviceId(Long v){deviceId=v;}
    public String getEncryptedSecret(){return encryptedSecret;} public void setEncryptedSecret(String v){encryptedSecret=v;}
    public RemoteSupportStatus getStatus(){return status;} public void setStatus(RemoteSupportStatus v){status=v;}
    public LocalDateTime getRotatedAt(){return rotatedAt;} public void setRotatedAt(LocalDateTime v){rotatedAt=v;}
}