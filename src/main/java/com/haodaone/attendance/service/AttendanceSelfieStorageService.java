package com.haodaone.attendance.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.document.service.DocumentStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.LocalDate;
import java.util.UUID;

/**
 * S3 storage for check-in selfies. Objects live in the employee-document bucket under
 * {@code attendance-selfies/{companyId}/{employeeId}/{yyyy-MM-dd}/{uuid}.jpg|png}.
 * Nothing here is public: images are streamed back through an authorised endpoint.
 */
@Service
public class AttendanceSelfieStorageService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceSelfieStorageService.class);
    public static final String KEY_PREFIX = "attendance-selfies/";

    private final S3Client s3Client;

    @Value("${app.storage.employee-document-bucket}")
    private String bucketName;

    public AttendanceSelfieStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String store(SelfieImage image, Long companyId, Long employeeId, LocalDate date) {
        if (image == null || companyId == null || employeeId == null || date == null) {
            throw new BadRequestException("Selfie, company, employee and date are required.");
        }
        String key = KEY_PREFIX + companyId + "/" + employeeId + "/" + date + "/"
                + UUID.randomUUID() + "." + image.extension();
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(image.contentType())
                            .contentLength((long) image.bytes().length)
                            .build(),
                    RequestBody.fromBytes(image.bytes()));
            return key;
        } catch (SdkException e) {
            log.error("S3 upload failed for selfie key '{}': {}", key, e.getMessage(), e);
            throw new DocumentStorageException("Selfie storage is temporarily unavailable. Please try again.");
        }
    }

    public byte[] read(String key) {
        if (key == null || key.isBlank()) throw new BadRequestException("No selfie is stored for this check-in.");
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucketName).key(key).build())
                    .asByteArray();
        } catch (NoSuchKeyException e) {
            throw new BadRequestException("The selfie for this check-in is no longer available.");
        } catch (SdkException e) {
            log.error("S3 download failed for selfie key '{}': {}", key, e.getMessage(), e);
            throw new DocumentStorageException("Selfie storage is temporarily unavailable. Please try again.");
        }
    }

    /** Deletes the object; S3 delete is idempotent, so a missing key is not an error. */
    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
    }

    public static String contentTypeForKey(String key) {
        return key != null && key.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
    }
}
