package com.haodaone.employee.dto;

public record InvitationValidationResponse(boolean valid, String employeeName, String email, String expiresAt) {
}