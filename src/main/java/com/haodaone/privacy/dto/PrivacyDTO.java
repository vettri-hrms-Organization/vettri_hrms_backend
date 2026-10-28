package com.haodaone.privacy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response shapes for the privacy module (DPDP Act 2023). */
public final class PrivacyDTO {

    private PrivacyDTO() {
    }

    // ---- Legal documents & acceptance ----

    public record LegalDocumentInfo(String type, String title, String version, String url) {}

    public record LegalDocumentHistory(String type, String title, String version, String url,
                                       LocalDateTime publishedAt, boolean current) {}

    public record LegalStatus(List<LegalDocumentInfo> documents, List<LegalDocumentInfo> pending,
                              boolean acceptanceRequired, boolean companyAdmin) {}

    public record AcceptRequest(List<String> documentTypes) {}

    public record AcceptanceView(Long id, Long userId, String userName, String userEmail, String docType,
                                 String version, LocalDateTime acceptedAt, String ipAddress, String userAgent,
                                 boolean onBehalfOfCompany) {}

    // ---- Company privacy settings ----

    public record PrivacySettingsView(String grievanceOfficerName, String grievanceOfficerEmail,
                                      String grievanceOfficerPhone, String breachContactName,
                                      String breachContactEmail, String breachContactPhone,
                                      int payrollRetentionYears, int otherDataRetentionMonths,
                                      boolean monitoringConsentRequired, boolean stopMonitoringOnWithdrawal) {}

    public record PrivacySettingsRequest(
            @Size(max = 150) String grievanceOfficerName,
            @Email(message = "Enter a valid grievance officer email") @Size(max = 150) String grievanceOfficerEmail,
            @Size(max = 30) String grievanceOfficerPhone,
            @Size(max = 150) String breachContactName,
            @Email(message = "Enter a valid breach contact email") @Size(max = 150) String breachContactEmail,
            @Size(max = 30) String breachContactPhone,
            @Min(value = 7, message = "Payroll and statutory records must be kept for at least 7 years")
            @Max(value = 30, message = "Payroll retention cannot exceed 30 years") Integer payrollRetentionYears,
            @Min(value = 0, message = "Retention cannot be negative")
            @Max(value = 360, message = "Retention cannot exceed 360 months") Integer otherDataRetentionMonths,
            Boolean monitoringConsentRequired,
            Boolean stopMonitoringOnWithdrawal) {}

    /** What every employee may see: who to contact about their personal data. */
    public record PrivacyContact(String companyName, String grievanceOfficerName, String grievanceOfficerEmail,
                                 String grievanceOfficerPhone, int otherDataRetentionMonths,
                                 int payrollRetentionYears) {}

    // ---- Monitoring consent ----

    public record MonitoringNotice(String version, String url, String purpose, List<String> collected,
                                   List<String> notCollected, String withdrawal) {}

    public record ConsentView(Long id, Long employeeId, String employeeName, String noticeVersion, String status,
                              LocalDateTime grantedAt, LocalDateTime withdrawnAt, String withdrawalReason) {}

    public record MonitoringConsentStatus(boolean required, boolean consented, boolean hasEmployeeProfile,
                                          boolean stopMonitoringOnWithdrawal, MonitoringNotice notice,
                                          ConsentView current, List<ConsentView> history) {}

    public record WithdrawRequest(@Size(max = 1000) String reason) {}

    // ---- Data principal requests ----

    public record DataRequestCreate(@NotBlank(message = "Choose a request type") String type,
                                    @Size(max = 4000) String details) {}

    public record DataRequestView(Long id, Long employeeId, String employeeName, String employeeCode,
                                  String requestType, String status, String details, LocalDate dueDate,
                                  boolean overdue, String resolutionNote, LocalDateTime createdAt,
                                  LocalDateTime resolvedAt, LocalDateTime readyAt, LocalDateTime expiresAt,
                                  int downloadCount) {}

    public record DataRequestUpdate(String status, @Size(max = 4000) String resolutionNote) {}

    public record ErasureEligibility(boolean eligible, String reason, LocalDate eligibleFrom) {}

    public record ErasureResult(Long requestId, Long employeeId, List<String> anonymisedFields,
                                int documentsDeleted, List<String> retained) {}

    // ---- Company account deletion ----

    public record DeletionRequestCreate(@Size(max = 2000) String reason,
                                        @NotBlank(message = "Type the organization name to confirm") String confirmCompanyName) {}

    public record DeletionRequestView(Long id, Long companyId, String companyName, String status,
                                      LocalDateTime requestedAt, LocalDateTime scheduledFor, String reason,
                                      LocalDateTime cancelledAt, LocalDateTime completedAt, String completionNote) {}
}
