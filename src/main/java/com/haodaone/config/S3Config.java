package com.haodaone.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Wires up the AWS SDK v2 {@link S3Client} (upload/download/delete) and
 * {@link S3Presigner} (short-lived signed URLs) as Spring beans, backed by
 * the bucket/region/credentials configured in application.properties.
 *
 * Directly ported from HaodaAsset's {@code S3Config} - same two-mode
 * credentials resolution (static access key/secret if set, otherwise the
 * AWS SDK's default credentials chain), same reasoning: Render has no EC2
 * instance profile to fall back to, so production always goes through the
 * static-credentials path, while local dev can rely on the AWS CLI's
 * already-configured credentials if the env vars are left unset.
 *
 * Package is {@code com.haodaone.config} (not {@code recruitment.config})
 * deliberately - S3 is HaodaOne-wide infrastructure, the same way it's
 * app-wide infrastructure in HaodaAsset, even though the Recruitment
 * module (resumes) is its only consumer today.
 */
@Configuration
public class S3Config {

    private static final Logger log = LoggerFactory.getLogger(S3Config.class);

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.accessKeyId:}")
    private String accessKeyId;

    @Value("${aws.secretKey:}")
    private String secretKey;

    private AwsCredentialsProvider credentialsProvider() {
        if (accessKeyId != null && !accessKeyId.isBlank() && secretKey != null && !secretKey.isBlank()) {
            log.info("S3Config: using static AWS credentials supplied via configuration/environment variables.");
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId.trim(), secretKey.trim()));
        }
        log.info("S3Config: no static AWS credentials configured - falling back to the AWS default credentials chain.");
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .build();
    }
}
