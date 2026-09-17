package com.haodaone.employee.dto;

public record InvitationResponse(String message, String status, String inviteUrl, String expiresAt) {
}