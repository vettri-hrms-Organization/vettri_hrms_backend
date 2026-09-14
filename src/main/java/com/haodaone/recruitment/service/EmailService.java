package com.haodaone.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.employee.entity.Employee;
import com.haodaone.recruitment.entity.Candidate;
import com.haodaone.recruitment.entity.Interview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transactional email for the Recruitment module, via Brevo's REST API
 * rather than SMTP - Render blocks outbound SMTP, same constraint and
 * provider HaodaAsset hit for invoice emails (see that app's
 * JavaMailSender-to-Brevo migration). No new Maven dependency: this uses
 * the JDK's built-in java.net.http.HttpClient and the Jackson
 * ObjectMapper Spring Boot already wires in, rather than pulling in a
 * Brevo SDK.
 *
 * If app.email.brevo-api-key isn't set (e.g. local dev without a key
 * configured), sendEmail logs the would-be email at INFO instead of
 * failing the calling workflow - assigning a manager, generating an
 * offer, etc. should never fail *because* email delivery isn't
 * configured yet.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");
    private static final URI BREVO_ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.email.brevo-api-key}")
    private String brevoApiKey;

    @Value("${app.email.from-address}")
    private String fromAddress;

    @Value("${app.email.from-name}")
    private String fromName;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** To the hiring manager, when HR assigns them a candidate for the manager round. */
    public void sendManagerAssignmentEmail(Candidate candidate, Interview interview, Employee manager) {
        if (manager.getEmail() == null || manager.getEmail().isBlank()) {
            log.warn("Manager {} has no email on file - skipping manager-assignment email for candidate {}",
                    manager.getFullName(), candidate.getFullName());
            return;
        }

        String subject = "Candidate Assigned for Manager Interview";
        String myInterviewsLink = frontendUrl + "/my-interviews";
        String body = "<p>Hi " + escape(manager.getFullName()) + ",</p>"
                + "<p>You've been assigned to conduct the manager interview for the following candidate:</p>"
                + "<table style=\"border-collapse:collapse;margin:16px 0;\">"
                + row("Candidate Name", escape(candidate.getFullName()))
                + row("Applied Position", escape(candidate.getJobOpening().getTitle()))
                + row("Department", escape(candidate.getJobOpening().getDepartment() != null ? candidate.getJobOpening().getDepartment().getName() : "-"))
                + row("Candidate Email", escape(candidate.getEmail()))
                + row("Candidate Mobile Number", escape(candidate.getPhone() != null ? candidate.getPhone() : "-"))
                + row("Resume", "<a href=\"" + myInterviewsLink + "\">View in My Interviews</a>")
                + row("Interview Date", interview.getScheduledAt().format(DATE_FMT))
                + row("Interview Time", interview.getScheduledAt().format(TIME_FMT))
                + row("Google Meet Link", "<a href=\"" + escape(interview.getMeetingLink()) + "\">" + escape(interview.getMeetingLink()) + "</a>")
                + "</table>"
                + (interview.getInstructions() != null && !interview.getInstructions().isBlank()
                        ? "<p><strong>Interview Instructions:</strong> " + escape(interview.getInstructions()) + "</p>"
                        : "")
                + "<p>Please record your rating and decision in HaodaOne after the interview.</p>";

        send(manager.getEmail(), manager.getFullName(), subject, body);
    }

    /** To the candidate, once HR schedules their manager-round interview. */
    public void sendCandidateManagerRoundEmail(Candidate candidate, Interview interview, Employee manager) {
        String subject = "Manager Interview Scheduled";
        String body = "<p>Dear " + escape(candidate.getFirstName()) + ",</p>"
                + "<p>Congratulations! You've cleared the initial interview and have been scheduled for the next round.</p>"
                + "<table style=\"border-collapse:collapse;margin:16px 0;\">"
                + row("Candidate Name", escape(candidate.getFullName()))
                + row("Position Applied", escape(candidate.getJobOpening().getTitle()))
                + row("Department", escape(candidate.getJobOpening().getDepartment() != null ? candidate.getJobOpening().getDepartment().getName() : "-"))
                + row("Hiring Manager", escape(manager.getFullName()))
                + row("Interview Date", interview.getScheduledAt().format(DATE_FMT))
                + row("Interview Time", interview.getScheduledAt().format(TIME_FMT))
                + row("Google Meet Link", "<a href=\"" + escape(interview.getMeetingLink()) + "\">" + escape(interview.getMeetingLink()) + "</a>")
                + "</table>"
                + (interview.getInstructions() != null && !interview.getInstructions().isBlank()
                        ? "<p><strong>Interview Instructions:</strong> " + escape(interview.getInstructions()) + "</p>"
                        : "")
                + "<p>We look forward to speaking with you. Best of luck!</p>";

        send(candidate.getEmail(), candidate.getFullName(), subject, body);
    }

    /**
     * To the candidate, once HR uploads the signed offer letter and
     * clicks "Send Offer Letter" (or "Resend"). The uploaded document
     * itself travels as an email attachment - this is the only place an
     * offer letter is emailed; generating the offer (setting
     * offerAmount/expectedJoiningDate) no longer sends anything by
     * itself. Returns true only when the email was actually accepted for
     * delivery, so the caller can persist a real Sent/Failed status
     * rather than assuming success.
     */
    public boolean sendOfferLetterEmail(Candidate candidate, byte[] offerLetterBytes, String offerLetterFilename) {
        String subject = "Your Offer Letter from Haoda";
        String body = "<p>Dear " + escape(candidate.getFirstName()) + ",</p>"
                + "<p>Congratulations! Please find attached your offer letter for the position of "
                + "<strong>" + escape(candidate.getJobOpening().getTitle()) + "</strong>.</p>"
                + "<table style=\"border-collapse:collapse;margin:16px 0;\">"
                + row("Position", escape(candidate.getJobOpening().getTitle()))
                + (candidate.getOfferAmount() != null ? row("Offered CTC", candidate.getOfferAmount().toString()) : "")
                + (candidate.getExpectedJoiningDate() != null ? row("Expected Joining Date", candidate.getExpectedJoiningDate().format(DateTimeFormatter.ofPattern("d MMMM yyyy"))) : "")
                + "</table>"
                + "<p>Please review the attached offer letter and reply to confirm your acceptance so we can proceed with onboarding.</p>";

        return sendWithAttachment(candidate.getEmail(), candidate.getFullName(), subject, body, offerLetterBytes, offerLetterFilename);
    }

    /** To the new hire, once accepting the offer auto-creates their employee login. */
    public void sendEmployeeWelcomeEmail(String toEmail, String toName, String employeeCode, String username, String temporaryPassword) {
        String subject = "Welcome to Haoda - Your Login Details";
        String loginUrl = frontendUrl + "/login";
        String body = "<p>Dear " + escape(toName) + ",</p>"
                + "<p>Welcome aboard! Your employee account has been created.</p>"
                + "<table style=\"border-collapse:collapse;margin:16px 0;\">"
                + row("Employee ID", escape(employeeCode))
                + row("Username", escape(username))
                + row("Temporary Password", escape(temporaryPassword))
                + row("Login", "<a href=\"" + loginUrl + "\">" + loginUrl + "</a>")
                + "</table>"
                + "<p>You'll be asked to set a new password the first time you log in.</p>";

        send(toEmail, toName, subject, body);
    }

    public boolean sendEmployeeInvitationEmail(String toEmail, String toName, String employeeCode,
                                               String rawToken, LocalDateTime expiresAt) {
        String subject = "You're invited to Vettri HRMS";
        String activationLink = frontendUrl + "/activate-account?token=" + java.net.URLEncoder.encode(rawToken, java.nio.charset.StandardCharsets.UTF_8);
        String body = "<div style=\"font-family:Arial,sans-serif;color:#17212b;max-width:600px;\">"
                + "<h1 style=\"color:#0b6e69;\">Vettri HRMS</h1>"
                + "<p>Hi " + escape(toName) + ",</p>"
                + "<p>Your Vettri HRMS employee account has been created.</p>"
                + row("Employee ID", escape(employeeCode))
                + "<p>Set your password to activate your account and access your employee dashboard.</p>"
                + "<p><a href=\"" + escape(activationLink) + "\" style=\"display:inline-block;padding:12px 20px;background:#0b6e69;color:#fff;text-decoration:none;border-radius:4px;\">Set Password / Activate Account</a></p>"
                + "<p>This invitation expires on " + escape(expiresAt.toString()) + ". Please request a new invitation if it has expired.</p>"
                + "<p>Regards,<br>Vettri HRMS</p></div>";
        return sendAndReport(toEmail, toName, subject, body);
    }

    public void sendAgentTokenOtpEmail(String toEmail, String toName, String otp, int expiryMinutes, String deviceName) {
        String subject = "Haoda agent token verification code";
        String body = "<p>Hello " + escape(toName) + ",</p>"
                + "<p>Use this one-time code to rotate the agent token for <strong>" + escape(deviceName) + "</strong>:</p>"
                + "<p style=\"font-size:28px;font-weight:700;letter-spacing:6px\">" + escape(otp) + "</p>"
                + "<p>This code expires in " + expiryMinutes + " minutes and can be used once.</p>";
        send(toEmail, toName, subject, body);
    }

    private void send(String toEmail, String toName, String subject, String htmlBody) {
        sendAndReport(toEmail, toName, subject, htmlBody);
    }

    private boolean sendAndReport(String toEmail, String toName, String subject, String htmlBody) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.info("BREVO_API_KEY not configured - not sending email. To: {} <{}>, Subject: {}", toName, toEmail, subject);
            return false;
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sender", Map.of("email", fromAddress, "name", fromName));
            payload.put("to", List.of(Map.of("email", toEmail, "name", toName != null ? toName : toEmail)));
            payload.put("subject", subject);
            payload.put("htmlContent", htmlBody);

            String json = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(BREVO_ENDPOINT)
                    .header("api-key", brevoApiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Sent email to {} <{}>: {}", toName, toEmail, subject);
                return true;
            } else {
                // Deliberately not thrown - a failed email shouldn't roll back the
                // candidate/interview state change that triggered it. Log loudly so
                // it's visible, and let HR notice + manually follow up if needed.
                log.error("Brevo email send failed ({}) to {} <{}>: {}", response.statusCode(), toName, toEmail, response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to send email to {} <{}>: {}", toName, toEmail, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Same as {@link #send} but with a single file attached (base64-encoded
     * inline in the JSON payload, per Brevo's API) and a boolean result
     * instead of a swallowed exception - the caller (offer letter send/
     * resend) needs to record a real Sent/Failed status, unlike the other
     * notification emails in this class which are fire-and-forget.
     *
     * When BREVO_API_KEY isn't configured, this logs and returns true
     * rather than false: same reasoning as {@link #send} - an unconfigured
     * environment isn't a delivery *failure*, and HR's "Send Offer
     * Letter" action (stage change, upload, etc.) shouldn't be reported
     * as failed just because local/dev email isn't wired up.
     */
    private boolean sendWithAttachment(String toEmail, String toName, String subject, String htmlBody,
                                        byte[] attachmentBytes, String attachmentFilename) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.info("BREVO_API_KEY not configured - not sending email. To: {} <{}>, Subject: {}", toName, toEmail, subject);
            return true;
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sender", Map.of("email", fromAddress, "name", fromName));
            payload.put("to", List.of(Map.of("email", toEmail, "name", toName != null ? toName : toEmail)));
            payload.put("subject", subject);
            payload.put("htmlContent", htmlBody);
            String encodedAttachment = java.util.Base64.getEncoder().encodeToString(attachmentBytes);
            payload.put("attachment", List.of(Map.of("content", encodedAttachment, "name", attachmentFilename)));

            String json = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(BREVO_ENDPOINT)
                    .header("api-key", brevoApiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Sent email with attachment to {} <{}>: {}", toName, toEmail, subject);
                return true;
            }
            log.error("Brevo email send failed ({}) to {} <{}>: {}", response.statusCode(), toName, toEmail, response.body());
            return false;
        } catch (Exception e) {
            log.error("Failed to send email with attachment to {} <{}>: {}", toName, toEmail, e.getMessage(), e);
            return false;
        }
    }

    private String row(String label, String value) {
        return "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">" + escape(label) + "</td>"
                + "<td style=\"padding:4px 0;font-weight:600;\">" + value + "</td></tr>";
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
