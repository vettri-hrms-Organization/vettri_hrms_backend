package com.haodaone.expense.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.document.service.DocumentStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * S3 storage for expense receipts and announcement attachments. Same bucket
 * and conventions as EmployeeDocumentS3StorageService, but restricted to
 * PDF/JPG/PNG and at most 10 MB. Callers pass a key prefix that already
 * contains the company id so objects stay tenant-partitioned.
 */
@Service
public class AttachmentStorageService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStorageService.class);
    static final long MAX_BYTES = 10L * 1024L * 1024L;

    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png");

    private final S3Client s3Client;

    @Value("${app.storage.employee-document-bucket}")
    private String bucketName;

    public AttachmentStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public StoredAttachment store(MultipartFile file, String keyPrefix) {
        validate(file);
        String originalName = originalNameOf(file);
        String extension = extensionOf(originalName);
        String contentType = CONTENT_TYPE_BY_EXTENSION.get(extension);
        String key = keyPrefix + "/" + UUID.randomUUID() + "." + extension;
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return new StoredAttachment(key, originalName, file.getSize(), contentType);
        } catch (IOException e) {
            log.error("Failed to read uploaded attachment '{}': {}", originalName, e.getMessage(), e);
            throw new DocumentStorageException("File storage is temporarily unavailable. Please try again.");
        } catch (SdkException e) {
            log.error("S3 upload failed for key '{}': {}", key, e.getMessage(), e);
            throw new DocumentStorageException("File storage is temporarily unavailable. Please try again.");
        }
    }

    public InputStreamResource retrieve(String key) {
        if (key == null || key.isBlank()) throw new BadRequestException("No file is attached.");
        try {
            ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucketName).key(key).build());
            return new InputStreamResource(stream);
        } catch (NoSuchKeyException e) {
            throw new BadRequestException("The file was not found in storage.");
        } catch (SdkException e) {
            log.error("S3 download failed for key '{}': {}", key, e.getMessage(), e);
            throw new DocumentStorageException("File storage is temporarily unavailable. Please try again.");
        }
    }

    /** Best effort - a stale object is harmless, a failed request should not be. */
    public void deleteQuietly(String key) {
        if (key == null || key.isBlank()) return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
        } catch (SdkException e) {
            log.warn("S3 delete failed for key '{}': {}", key, e.getMessage());
        }
    }

    static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Please attach a file.");
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("File is too large. Maximum allowed is 10 MB.");
        }
        String originalName = originalNameOf(file);
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new BadRequestException("Invalid file name.");
        }
        String extension = extensionOf(originalName);
        String expected = CONTENT_TYPE_BY_EXTENSION.get(extension);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (expected == null || !expected.equals(contentType)) {
            throw new BadRequestException("Only PDF, JPG, or PNG files are accepted.");
        }
    }

    private static String originalNameOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (name == null || name.isBlank()) ? "file" : name.trim();
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot < 0 || dot == filename.length() - 1) ? "" : filename.substring(dot + 1).toLowerCase();
    }

    public record StoredAttachment(String key, String originalName, long sizeBytes, String contentType) {
    }
}
