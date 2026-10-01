package com.haodaone.document.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmployeeDocumentS3StorageServiceTest {

    private S3Client s3Client;
    private EmployeeDocumentS3StorageService storageService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        storageService = new EmployeeDocumentS3StorageService(s3Client, mock(S3Presigner.class));
        ReflectionTestUtils.setField(storageService, "bucketName", "vettri-hrms-documents-2026");
        ReflectionTestUtils.setField(storageService, "maxFileSizeMb", 10L);
    }

    @Test
    void storesDocxUnderDedicatedBucketAndTenantScopedKey() {
        MockMultipartFile file = new MockMultipartFile("file", "aadhaar.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "document".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("stored").build());

        EmployeeDocumentS3StorageService.StoredFile stored = storageService.store(file, 1L, 4268L, "AADHAAR");

        assertEquals("vettri-hrms-documents-2026", ReflectionTestUtils.getField(storageService, "bucketName"));
        assertTrue(Pattern.matches("employee-documents/1/4268/AADHAAR/[0-9a-f-]{36}\\.docx", stored.key()));
        UUID.fromString(stored.key().substring(stored.key().lastIndexOf('/') + 1, stored.key().length() - 5));
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertEquals("vettri-hrms-documents-2026", request.getValue().bucket());
        assertEquals(stored.key(), request.getValue().key());
    }

    @Test
    void mapsS3UploadFailureToSafeStorageException() {
        MockMultipartFile file = new MockMultipartFile("file", "aadhaar.pdf", "application/pdf", "document".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("AccessDenied: internal details").statusCode(403).build());

        DocumentStorageException exception = assertThrows(DocumentStorageException.class,
                () -> storageService.store(file, 1L, 4268L, "AADHAAR"));

        assertEquals("Document storage is temporarily unavailable. Please try again.", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void rejectsUnsupportedFileExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "aadhaar.exe",
                "application/octet-stream", "document".getBytes());

        assertThrows(com.haodaone.common.exception.BadRequestException.class,
                () -> storageService.store(file, 1L, 4268L, "AADHAAR"));
    }

        @Test
        void rejectsAllowedExtensionWithMismatchedMimeType() {
                MockMultipartFile file = new MockMultipartFile("file", "aadhaar.pdf", "image/jpeg", "document".getBytes());

                com.haodaone.common.exception.BadRequestException exception = assertThrows(
                                com.haodaone.common.exception.BadRequestException.class,
                                () -> storageService.store(file, 1L, 4268L, "AADHAAR"));

                assertEquals("Only PDF, JPG, PNG, DOC, DOCX, or TXT documents are accepted.", exception.getMessage());
        }
}