package com.haodaone.software.service;

import com.haodaone.common.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.security.DigestInputStream;

@Service
public class SoftwarePackageStorageService {
    private static final Logger log = LoggerFactory.getLogger(SoftwarePackageStorageService.class);
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${app.upload.max-software-package-size-mb:1024}")
    private long maxFileSizeMb;

    @Value("${aws.s3.presigned-url-expiry-minutes:15}")
    private long presignedUrlExpiryMinutes;

    public SoftwarePackageStorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    public StoredFile store(MultipartFile file, Long companyId) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Please upload an installer file");
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()
                || originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")
                || !originalName.toLowerCase().endsWith(".exe")) {
            throw new BadRequestException("Only a safe .exe installer filename is accepted");
        }
        if (file.getSize() > maxFileSizeMb * 1024L * 1024L)
            throw new BadRequestException("Installer exceeds the maximum allowed size");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String key = "software/" + companyId + "/" + UUID.randomUUID() + ".exe";
            try (InputStream inputStream = file.getInputStream();
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType("application/vnd.microsoft.portable-executable")
                        .contentLength(file.getSize())
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(digestInputStream, file.getSize()));
                String checksum = HexFormat.of().formatHex(digest.digest());
                log.info("Stored software installer for company {} as private object {}", companyId, key);
                return new StoredFile(key, checksum, file.getSize(), originalName);
            }
        } catch (IOException e) {
            log.error("Could not read installer upload for company {} and file {}", companyId, originalName, e);
            throw new IllegalStateException("Could not read installer upload", e);
        } catch (S3Exception e) {
            log.error("S3 upload failed for software installer bucket={} key=software/{}/{}: {}", bucketName, companyId, originalName, e.getMessage(), e);
            throw new IllegalStateException("S3 upload failed while storing the installer file", e);
        } catch (Exception e) {
            log.error("Could not store installer upload for company {} and file {}", companyId, originalName, e);
            throw new IllegalStateException("Could not store installer upload", e);
        }
    }

    public String generateDownloadUrl(String key) {
        if (key == null || !key.startsWith("software/")) throw new BadRequestException("Invalid software storage reference");
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucketName).key(key).build();
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiryMinutes)).getObjectRequest(get).build())
                .url().toString();
    }

    public record StoredFile(String key, String checksumSha256, long sizeBytes, String originalName) {}
}
