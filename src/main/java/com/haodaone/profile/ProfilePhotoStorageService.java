package com.haodaone.profile;

import com.haodaone.attendance.service.SelfieImage;
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

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Profile photos live in the employee-document bucket under profile-photos/{random}.{jpg|png}.
 * The stored profile_photo_url is "/api/profile-photos/{random}.{ext}", served without login so it
 * works in plain img tags (avatars, org chart, birthdays); the random 128-bit name is the capability.
 */
@Service
public class ProfilePhotoStorageService {

    private static final Logger log = LoggerFactory.getLogger(ProfilePhotoStorageService.class);
    public static final String URL_PREFIX = "/api/profile-photos/";
    private static final String KEY_PREFIX = "profile-photos/";
    private static final Pattern NAME = Pattern.compile("^[0-9a-f]{32}\\.(jpg|png)$");

    private final S3Client s3Client;

    @Value("${app.storage.employee-document-bucket}")
    private String bucketName;

    public ProfilePhotoStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /** Validates (real JPEG/PNG signature, 1 MB max) and uploads; returns the public URL path. */
    public String store(String dataUrl) {
        SelfieImage image;
        try {
            image = SelfieImage.decode(dataUrl);
        } catch (BadRequestException invalid) {
            throw new BadRequestException("Profile photo must be a JPEG or PNG image of 1 MB or less");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + "." + image.extension();
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(KEY_PREFIX + name)
                            .contentType(image.contentType())
                            .contentLength((long) image.bytes().length)
                            .build(),
                    RequestBody.fromBytes(image.bytes()));
        } catch (SdkException e) {
            log.error("S3 upload failed for profile photo '{}': {}", name, e.getMessage(), e);
            throw new DocumentStorageException("Photo storage is temporarily unavailable. Please try again.");
        }
        return URL_PREFIX + name;
    }

    /** Bytes for a public photo name, or null when the name is malformed or the object is gone. */
    public byte[] read(String name) {
        if (name == null || !NAME.matcher(name).matches()) return null;
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucketName).key(KEY_PREFIX + name).build())
                    .asByteArray();
        } catch (NoSuchKeyException missing) {
            return null;
        } catch (SdkException e) {
            log.warn("Could not read profile photo '{}': {}", name, e.getMessage());
            return null;
        }
    }

    /** Best-effort removal of a photo previously stored by {@link #store}; other URLs are ignored. */
    public void deleteQuietly(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith(URL_PREFIX)) return;
        String name = publicUrl.substring(URL_PREFIX.length());
        if (!NAME.matcher(name).matches()) return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(KEY_PREFIX + name).build());
        } catch (SdkException e) {
            log.warn("Could not delete old profile photo '{}': {}", name, e.getMessage());
        }
    }

    public static String contentType(String name) {
        return name != null && name.endsWith(".png") ? "image/png" : "image/jpeg";
    }
}
