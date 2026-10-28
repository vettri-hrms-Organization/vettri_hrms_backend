package com.haodaone.letters.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.document.service.DocumentStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

/**
 * Byte-level S3 storage for generated letters and the letterhead logo, in
 * the same bucket as employee documents (app.storage.employee-document-bucket).
 * Employee letters use the EmployeeDocument key layout
 * (employee-documents/{company}/{employee}/LETTER/...) so the EmployeeDocument
 * record created alongside can point at the very same object.
 */
@Service
public class LetterStorageService {

    private static final Logger log = LoggerFactory.getLogger(LetterStorageService.class);

    private final S3Client s3Client;

    @Value("${app.storage.employee-document-bucket}")
    private String bucketName;

    public LetterStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String employeeLetterKey(Long companyId, Long employeeId) {
        return "employee-documents/" + companyId + "/" + employeeId + "/LETTER/" + UUID.randomUUID() + ".pdf";
    }

    public String candidateLetterKey(Long companyId, Long candidateId) {
        return "letters/" + companyId + "/candidates/" + candidateId + "/" + UUID.randomUUID() + ".pdf";
    }

    public String logoKey(Long companyId, String extension) {
        return "letterhead/" + companyId + "/logo-" + UUID.randomUUID() + "." + extension;
    }

    public void put(String key, byte[] bytes, String contentType) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) bytes.length)
                    .build(), RequestBody.fromBytes(bytes));
            log.info("Stored letter object '{}' ({} bytes)", key, bytes.length);
        } catch (SdkException e) {
            log.error("S3 upload failed for key '{}': {}", key, e.getMessage(), e);
            throw new DocumentStorageException("Document storage is temporarily unavailable. Please try again.");
        }
    }

    public byte[] get(String key) {
        if (key == null || key.isBlank()) throw new BadRequestException("No file exists for this letter.");
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucketName).key(key).build()).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new BadRequestException("Letter file not found in cloud storage.");
        } catch (SdkException e) {
            log.error("S3 download failed for key '{}': {}", key, e.getMessage(), e);
            throw new DocumentStorageException("Document storage is temporarily unavailable. Please try again.");
        }
    }
}
