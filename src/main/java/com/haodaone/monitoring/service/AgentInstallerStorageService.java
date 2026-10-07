package com.haodaone.monitoring.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Service
public class AgentInstallerStorageService {

    private static final String INSTALLER_KEY = "agent/HaodaOneAgentSetup.exe";

    private final S3Presigner presigner;

    @Value("${aws.s3.agent-installer-bucket:vettri-software-installer-bucket}")
    private String bucketName;

    @Value("${aws.s3.agent-installer-url-expiry-minutes:10}")
    private long expiryMinutes;

    public AgentInstallerStorageService(S3Presigner presigner) {
        this.presigner = presigner;
    }

    public String createDownloadUrl() {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("The Agent installer bucket is not configured.");
        }
        if (expiryMinutes < 1 || expiryMinutes > 60) {
            throw new IllegalStateException("The Agent installer URL expiry must be between 1 and 60 minutes.");
        }
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(INSTALLER_KEY)
                .responseContentDisposition("attachment; filename=\"HaodaOneAgentSetup.exe\"")
                .build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expiryMinutes))
                .getObjectRequest(get)
                .build())
                .url()
                .toString();
    }
}
