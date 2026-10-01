package com.haodaone.document.service;

import com.haodaone.common.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Service
public class EmployeeDocumentS3StorageService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeDocumentS3StorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "doc", "docx", "txt");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    );

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${app.upload.max-offer-letter-size-mb:10}")
    private long maxFileSizeMb;

    @Value("${aws.s3.presigned-url-expiry-minutes:15}")
    private long presignedUrlExpiryMinutes;

    public EmployeeDocumentS3StorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Please attach a document file.");
        validate(file);

        String originalName = originalNameOf(file);
        String extension = extensionOf(originalName).toLowerCase();
        String key = "employee-documents/" + UUID.randomUUID() + "." + extension;
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "application/octet-stream";

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded employee document '{}' to S3 as key '{}' in bucket '{}'", originalName, key, bucketName);
            return new StoredFile(key, originalName, file.getSize(), contentType);
        } catch (IOException e) {
            log.error("Failed to read uploaded document '{}': {}", originalName, e.getMessage(), e);
            throw new IllegalStateException("Failed to save the uploaded document. Please try again.", e);
        } catch (S3Exception e) {
            log.error("S3 upload failed for key '{}': {}", key, e.getMessage(), e);
            throw new IllegalStateException("Failed to save the uploaded document. Please try again.", e);
        }
    }

    public InputStreamResource retrieve(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("No document file exists for this record.");
        }
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(getRequest);
            log.info("Streaming employee document from S3 key '{}'", key);
            return new InputStreamResource(stream);
        } catch (NoSuchKeyException e) {
            throw new BadRequestException("Document file not found in cloud storage.");
        } catch (S3Exception e) {
            log.error("S3 download failed for key '{}': {}", key, e.getMessage(), e);
            throw new IllegalStateException("Failed to read the stored document.", e);
        }
    }

    public String generateDownloadUrl(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("No document file exists for this record.");
        }
        GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiryMinutes))
                .getObjectRequest(getRequest)
                .build()).url().toString();
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
        } catch (S3Exception e) {
            log.warn("Could not delete S3 object '{}': {}", key, e.getMessage());
        }
    }

    private void validate(MultipartFile file) {
        long maxBytes = maxFileSizeMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("File is too large (" + (file.getSize() / (1024 * 1024)) + "MB). Maximum allowed is " + maxFileSizeMb + "MB.");
        }

        String originalName = originalNameOf(file);
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new BadRequestException("Invalid file name.");
        }

        String extension = extensionOf(originalName).toLowerCase();
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        boolean extensionOk = ALLOWED_EXTENSIONS.contains(extension);
        boolean contentTypeOk = contentType.isBlank() || ALLOWED_CONTENT_TYPES.contains(contentType);

        if (!extensionOk || !contentTypeOk) {
            throw new BadRequestException("Only PDF, JPG, PNG, DOC, DOCX, or TXT documents are accepted.");
        }
    }

    private String originalNameOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (name == null || name.isBlank()) ? "document" : name.trim();
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot < 0 || dot == filename.length() - 1) ? "" : filename.substring(dot + 1);
    }

    public record StoredFile(String key, String originalName, long sizeBytes, String contentType) {}
}