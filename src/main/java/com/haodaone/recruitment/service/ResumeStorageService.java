package com.haodaone.recruitment.service;

import com.haodaone.common.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Handles resume file storage for public job applications.
 *
 * Deliberately local-disk, not S3 (unlike HaodaAsset's invoice storage) -
 * this app's build doesn't currently pull in the AWS SDK, and adding a new
 * dependency wasn't verifiable in the environment this was written in. The
 * method signatures here (store/retrieve/delete by opaque key) mirror
 * HaodaAsset's S3StorageService on purpose, so swapping this out for an
 * S3-backed implementation later is a contained change - nothing outside
 * this class needs to know how/where the bytes are kept.
 *
 * KNOWN LIMITATION: Render's local filesystem is ephemeral - it does not
 * survive a redeploy or restart. HaodaAsset hit exactly this problem with
 * invoice storage and migrated to S3 (see S3StorageService's own javadoc).
 * Until this module gets the same treatment, resumes uploaded through the
 * Careers page will be lost on the next deploy. Point app.upload.resume-dir
 * at a Render persistent disk mount if you add one, or prioritize an S3
 * migration before relying on this in steady-state production use.
 */
@Service
public class ResumeStorageService {

    private static final Logger log = LoggerFactory.getLogger(ResumeStorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    @Value("${app.upload.resume-dir:uploads/resumes}")
    private String resumeDir;

    @Value("${app.upload.max-resume-size-mb:10}")
    private long maxFileSizeMb;

    /** Validates and saves a resume to disk under a brand-new UUID key. Returns only the storage key - callers persist this, never a filesystem path. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please attach a resume file.");
        }
        validate(file);

        String originalName = originalNameOf(file);
        String extension = extensionOf(originalName).toLowerCase();
        String key = UUID.randomUUID() + "." + extension;

        try {
            // Resolve to an absolute path up front - resumeDir is configured as a
            // *relative* path by default (see app.upload.resume-dir), and comparing
            // a relative `dir` against an absolute target below always failed the
            // containment check, rejecting every upload regardless of the file.
            Path dir = Paths.get(resumeDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(key).normalize();
            if (!target.getParent().equals(dir)) {
                // Defense in depth: key is a fresh UUID we just generated, so this
                // can never actually trigger, but a storage layer that skips path
                // containment checks is exactly how key-traversal bugs slip in later.
                throw new BadRequestException("Invalid file key.");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Stored resume '{}' as key '{}'", originalName, key);
            return key;
        } catch (IOException e) {
            log.error("Failed to store resume '{}': {}", originalName, e.getMessage(), e);
            throw new IllegalStateException("Failed to save the uploaded resume. Please try again.", e);
        }
    }

    /** Opens the stored file for streaming back on a download request. Caller closes the stream. */
    public InputStreamResource retrieve(String key) {
        requireKey(key);
        Path path = Paths.get(resumeDir).resolve(key).normalize();
        if (!Files.exists(path)) {
            throw new BadRequestException("Resume file not found - it may not have survived a recent deploy (see ResumeStorageService's known limitation).");
        }
        try {
            return new InputStreamResource(Files.newInputStream(path));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read the stored resume.", e);
        }
    }

    /** Deletes a stored resume. Never throws - a missing file must not block the caller. */
    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        try {
            Files.deleteIfExists(Paths.get(resumeDir).resolve(key).normalize());
        } catch (IOException e) {
            log.warn("Could not delete resume '{}': {}", key, e.getMessage());
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
}