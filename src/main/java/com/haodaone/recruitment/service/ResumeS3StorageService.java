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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Handles resume file storage for public job applications in Amazon S3.
 *
 * Replaces the old local-disk {@code ResumeStorageService}, whose javadoc
 * flagged exactly this problem: Render's filesystem is ephemeral, so every
 * resume uploaded through the Careers page was silently lost on the next
 * deploy. This class is a direct adaptation of HaodaAsset's
 * {@code S3StorageService} (same AWS SDK v2 calls, same bucket-private /
 * stream-through-backend / UUID-key-never-original-filename design) - only
 * two things differ from that source on purpose:
 *
 *   1. Objects live under the {@code resumes/} prefix instead of
 *      {@code invoices/}, in the same bucket (see aws.s3.bucket-name).
 *   2. Validation accepts PDF, DOC, and DOCX (matching the old
 *      ResumeStorageService's rules exactly), not just PDF - resumes
 *      legitimately come in all three formats, unlike invoices.
 *
 * Method signatures ({@code store}/{@code retrieve}/{@code delete}, keyed
 * by an opaque String) are intentionally identical to the old
 * ResumeStorageService's, including retrieve()'s return type
 * (InputStreamResource, not AWS's ResponseInputStream directly) - so
 * CandidateService and CandidateController needed only a type-name swap,
 * not a rewrite, and the public API/DB column (Candidate.resumeFileKey)
 * are completely unchanged.
 *
 * What's persisted in Postgres (Candidate.resumeFileKey) is the S3 object
 * key only, e.g. "resumes/8f73d12c-3c8d-4b2b-a8d7-....pdf" - never a
 * filesystem path, never a full URL.
 */
@Service
public class ResumeS3StorageService {

    private static final Logger log = LoggerFactory.getLogger(ResumeS3StorageService.class);

    /** Same rule the old ResumeStorageService enforced - kept identical, just re-hosted here. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private static final String RESUME_PREFIX = "resumes/";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    /** Same config key the old ResumeStorageService used - no application.properties rename needed for this one. */
    @Value("${app.upload.max-resume-size-mb:10}")
    private long maxFileSizeMb;

    @Value("${aws.s3.presigned-url-expiry-minutes:15}")
    private long presignedUrlExpiryMinutes;

    public ResumeS3StorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    /**
     * Validates and uploads a resume to S3 under a brand-new UUID key.
     * Returns only the S3 object key (e.g. "resumes/3f2a1c9e-....pdf") -
     * callers persist this, exactly as before; never a filesystem path,
     * never a full URL.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please attach a resume file.");
        }
        validate(file);

        String originalName = originalNameOf(file);
        String extension = extensionOf(originalName).toLowerCase();
        String key = RESUME_PREFIX + UUID.randomUUID() + "." + extension;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded resume '{}' to S3 as key '{}' in bucket '{}'", originalName, key, bucketName);
            return key;
        } catch (IOException e) {
            log.error("Failed to read uploaded resume '{}': {}", originalName, e.getMessage(), e);
            throw new IllegalStateException("Failed to save the uploaded resume. Please try again.", e);
        } catch (S3Exception e) {
            log.error("S3 upload failed for key '{}': {}", key, errorMessageOf(e), e);
            throw new IllegalStateException("Failed to save the uploaded resume. Please try again.", e);
        }
    }

    /**
     * Opens a live stream directly from S3 for the given key, wrapped as an
     * InputStreamResource - same return type the old disk-backed
     * retrieve() had, so CandidateController's download endpoint needed no
     * changes beyond the field type it injects. Caller (Spring's response
     * writer) closes the stream once written.
     */
    public InputStreamResource retrieve(String key) {
        requireKey(key);
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getRequest);
            log.info("Streaming resume from S3 key '{}'", key);
            return new InputStreamResource(s3Stream);
        } catch (NoSuchKeyException e) {
            log.warn("Requested S3 key does not exist: {}", key);
            throw new BadRequestException("Resume file not found in cloud storage. It may have been removed.");
        } catch (S3Exception e) {
            log.error("S3 download failed for key '{}': {}", key, errorMessageOf(e), e);
            throw new IllegalStateException("Failed to read the stored resume.", e);
        }
    }

    /**
     * Deletes a stored resume from S3. Never throws - a missing/undeletable
     * object must not block the caller, same contract the old disk-backed
     * delete() had. Not currently called by any endpoint (there's no
     * candidate/resume delete flow yet), but ready the moment one exists.
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

    /**
     * Short-lived (default 15 minute) presigned GET URL for the given key.
     * Not used by the download endpoint (which streams through the backend
     * instead, so the existing RECRUITMENT_VIEW guard keeps applying) -
     * kept for parity with HaodaAsset's S3StorageService as a
     * general-purpose building block for a future use (e.g. emailing a
     * temporary resume link) without making the bucket public.
     */
    public String generateResumeUrl(String key) {
        requireKey(key);
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiryMinutes))
                .getObjectRequest(getRequest)
                .build();

        String url = s3Presigner.presignGetObject(presignRequest).url().toString();
        log.info("Generated presigned URL for S3 key '{}' (expires in {} minutes)", key, presignedUrlExpiryMinutes);
        return url;
    }

    /** Identical rules to the old ResumeStorageService.validate() - just re-hosted here. */
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
            throw new BadRequestException("Only PDF, DOC, or DOCX resumes are accepted.");
        }
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("No resume on file for this candidate.");
        }
    }

    private String originalNameOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (name == null || name.isBlank()) ? "resume" : name.trim();
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot < 0 || dot == filename.length() - 1) ? "" : filename.substring(dot + 1);
    }

    private String errorMessageOf(S3Exception e) {
        return e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
    }
}
