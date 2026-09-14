package com.haodaone.recruitment.service;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Handles offer-letter document storage in the same S3 bucket
 * ResumeS3StorageService uses (aws.s3.bucket-name), under a dedicated
 * {@code offer-letters/} prefix - same bucket-private / stream-through-
 * backend / UUID-key-never-original-filename design, same accepted
 * formats (PDF, DOC, DOCX). Kept as its own class rather than a
 * generalized "document storage" service so the two upload flows
 * (public, unauthenticated resumes vs. HR-only offer letters) stay
 * independently validated and easy to reason about separately, matching
 * how ResumeS3StorageService itself was kept separate from HaodaAsset's
 * invoice storage service.
 *
 * What's persisted in Postgres (Candidate.offerLetterFileKey) is the S3
 * object key only, e.g. "offer-letters/8f73d12c-....pdf" - never a
 * filesystem path, never a full URL.
 */
@Service
public class OfferLetterS3StorageService {

    private static final Logger log = LoggerFactory.getLogger(OfferLetterS3StorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private static final String OFFER_LETTER_PREFIX = "offer-letters/";

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${app.upload.max-offer-letter-size-mb:10}")
    private long maxFileSizeMb;

    public OfferLetterS3StorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Validates and uploads an offer letter to S3 under a brand-new UUID
     * key. Returns only the S3 object key - callers persist that
     * (Candidate.offerLetterFileKey), never a filesystem path or URL.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please attach an offer letter file.");
        }
        validate(file);

        String originalName = originalNameOf(file);
        String extension = extensionOf(originalName).toLowerCase();
        String key = OFFER_LETTER_PREFIX + UUID.randomUUID() + "." + extension;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded offer letter '{}' to S3 as key '{}' in bucket '{}'", originalName, key, bucketName);
            return key;
        } catch (IOException e) {
            log.error("Failed to read uploaded offer letter '{}': {}", originalName, e.getMessage(), e);
            throw new IllegalStateException("Failed to save the uploaded offer letter. Please try again.", e);
        } catch (S3Exception e) {
            log.error("S3 upload failed for key '{}': {}", key, errorMessageOf(e), e);
            throw new IllegalStateException("Failed to save the uploaded offer letter. Please try again.", e);
        }
    }

    /**
     * Opens a live stream directly from S3 for the given key, wrapped as
     * an InputStreamResource - used for both preview (viewed inline in
     * the browser) and download of the currently-uploaded offer letter.
     */
    public InputStreamResource retrieve(String key) {
        requireKey(key);
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getRequest);
            log.info("Streaming offer letter from S3 key '{}'", key);
            return new InputStreamResource(s3Stream);
        } catch (NoSuchKeyException e) {
            log.warn("Requested S3 key does not exist: {}", key);
            throw new BadRequestException("Offer letter file not found in cloud storage. It may have been removed.");
        } catch (S3Exception e) {
            log.error("S3 download failed for key '{}': {}", key, errorMessageOf(e), e);
            throw new IllegalStateException("Failed to read the stored offer letter.", e);
        }
    }

    /** Full bytes of the stored offer letter - used to build the email attachment when HR clicks "Send Offer Letter". */
    public byte[] retrieveBytes(String key) {
        requireKey(key);
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getRequest)) {
                return readAllBytes(s3Stream);
            }
        } catch (NoSuchKeyException e) {
            log.warn("Requested S3 key does not exist: {}", key);
            throw new BadRequestException("Offer letter file not found in cloud storage. It may have been removed.");
        } catch (IOException e) {
            log.error("Failed to read offer letter bytes for key '{}': {}", key, e.getMessage(), e);
            throw new IllegalStateException("Failed to read the stored offer letter.", e);
        } catch (S3Exception e) {
            log.error("S3 download failed for key '{}': {}", key, errorMessageOf(e), e);
            throw new IllegalStateException("Failed to read the stored offer letter.", e);
        }
    }

    /**
     * Deletes a stored offer letter from S3. Never throws - a missing or
     * undeletable object must not block the caller (e.g. replacing an
     * offer letter with a new upload).
     */
    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteRequest);
            log.info("Deleted S3 object '{}' from bucket '{}'", key, bucketName);
        } catch (S3Exception e) {
            log.warn("Could not delete S3 object '{}': {}", key, errorMessageOf(e));
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
        String contentType = file.getContentType();
        boolean extensionOk = ALLOWED_EXTENSIONS.contains(extension);
        boolean contentTypeOk = contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase());

        if (!extensionOk || !contentTypeOk) {
            throw new BadRequestException("Only PDF, DOC, or DOCX offer letters are accepted.");
        }
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("No offer letter on file for this candidate.");
        }
    }

    private String originalNameOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (name == null || name.isBlank()) ? "offer-letter" : name.trim();
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot < 0 || dot == filename.length() - 1) ? "" : filename.substring(dot + 1);
    }

    private byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private String errorMessageOf(S3Exception e) {
        return e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
    }
}
