package com.haodaone.document.controller;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.document.dto.EmployeeDocumentDTO;
import com.haodaone.document.service.EmployeeDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeDocumentControllerDateTest {

    @Test
    void malformedDateUsesClientValidationError() {
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> EmployeeDocumentController.parseDate("expiryDate", "10/02/2026", false));

        assertEquals("expiryDate must use ISO format yyyy-MM-dd.", exception.getMessage());
    }

    @Test
    void omittedOptionalExpiryDateParsesAsNull() {
        assertNull(EmployeeDocumentController.parseDate("expiryDate", null, true));
        assertNull(EmployeeDocumentController.parseDate("expiryDate", "", true));
    }

    @Test
    void aadhaarUploadPassesMissingDatesAndNumberToService() {
        EmployeeDocumentService service = mock(EmployeeDocumentService.class);
        EmployeeDocumentController controller = new EmployeeDocumentController(service);
        MockMultipartFile file = new MockMultipartFile("file", "aadhaar.pdf", "application/pdf", new byte[]{1});
        when(service.upload(42L, file, "AADHAAR", null, null, null, null)).thenReturn(new EmployeeDocumentDTO());

        var response = controller.upload(42L, file, "AADHAAR", null, null, null, null);

        assertEquals(201, response.getStatusCode().value());
        verify(service).upload(42L, file, "AADHAAR", null, null, null, null);
    }
}