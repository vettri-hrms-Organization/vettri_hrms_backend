package com.haodaone.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RegisterRequest(
        @NotBlank(message = "First name is required") @Size(max = 75) String firstName,
        @NotBlank(message = "Last name is required") @Size(max = 75) String lastName,
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") @Size(max = 150) String email,
        @NotBlank(message = "Password is required") @Size(min = 8, max = 128) String password,
        @Size(max = 100) String role,
        @NotBlank(message = "Organization name is required") @Size(max = 255) String organizationName,
        @NotBlank(message = "Industry is required") @Size(max = 100) String industry,
        @NotBlank(message = "Company size is required") @Size(max = 30) String companySize,
        @NotBlank(message = "Country is required") @Size(max = 100) String country,
        List<@Size(max = 100) String> interests,
        @Size(max = 50) String plan
) { }
