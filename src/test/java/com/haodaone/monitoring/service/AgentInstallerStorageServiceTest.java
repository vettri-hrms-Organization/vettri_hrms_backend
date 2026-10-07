package com.haodaone.monitoring.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentInstallerStorageServiceTest {

    @Test
    void signsTheVettriAgentInstallerObject() {
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("testAccessKey", "testSecretKey")))
                .build()) {
            AgentInstallerStorageService service = new AgentInstallerStorageService(presigner);
            ReflectionTestUtils.setField(service, "bucketName", "vettri-software-installer-bucket");
            ReflectionTestUtils.setField(service, "expiryMinutes", 10L);

            URI signedUri = URI.create(service.createDownloadUrl());
            String query = URLDecoder.decode(signedUri.getRawQuery(), StandardCharsets.UTF_8);
            assertEquals("vettri-software-installer-bucket.s3.amazonaws.com", signedUri.getHost());
            assertEquals("/agent/VettriAgentSetup.exe", signedUri.getPath());
            assertTrue(query.contains("X-Amz-Signature="));
            assertTrue(query.contains("X-Amz-Credential=testAccessKey/"));
            assertTrue(query.contains("/us-east-1/s3/aws4_request"));
            assertTrue(query.contains("response-content-disposition="));
        }
    }
}
