package com.haodaone.document.controller;

import com.haodaone.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeDocumentControllerDateTest {

    @Test
    void malformedDateUsesClientValidationError() {
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> EmployeeDocumentController.parseDate("expiryDate", "10/02/2026", false));

        assertEquals("expiryDate must use ISO format yyyy-MM-dd.", exception.getMessage());
    }

    @Test
    void expiryDateRemainsRequiredByDocumentModel() {
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> EmployeeDocumentController.parseDate("expiryDate", "", false));

        assertEquals("expiryDate is required.", exception.getMessage());
    }
}