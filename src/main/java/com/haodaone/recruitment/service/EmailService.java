package com.haodaone.recruitment.service;

import com.haodaone.employee.entity.Employee;
import com.haodaone.recruitment.entity.Candidate;
import com.haodaone.recruitment.entity.Interview;
import jakarta.mail.internet.InternetAddress;
import org.springframework.core.io.ByteArrayResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Shared transactional email sender for recruitment, onboarding, and
 * agent-token notifications. Credentials and SMTP settings are supplied
 * by Spring Boot from environment variables; the service owns the sender
 * address so callers cannot choose an arbitrary From address.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");
    private final JavaMailSender mailSender;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.email.from-address}")
    private String fromAddress;

    @Value("${app.email.from-name}")
    private String fromName;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** To the hiring manager, when HR assigns them a candidate for the manager round. */
    public void sendManagerAssignmentEmail(Candidate candidate, Interview interview, Employee manager) {
        if (manager.getEmail() == null || manager.getEmail().isBlank()) {
            log.warn("Manager {} has no email on file - skipping manager-assignment email for candidate {}",
                    manager.getFullName(), candidate.getFullName());
            return;
        }

        String subject = "Candidate Assigned for Manager Interview";
        String myInterviewsLink = applicationUrl() + "/my-interviews";
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
                + "<p>Please record your rating and decision in Vettri HRMS after the interview.</p>";

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
        String subject = "Your Offer Letter from Vettri HRMS";
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
        String subject = "Welcome to Vettri HRMS - Your Login Details";
        String loginUrl = applicationUrl() + "/login";
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
        String activationLink = applicationUrl() + "/activate-account?token=" + java.net.URLEncoder.encode(rawToken, java.nio.charset.StandardCharsets.UTF_8);
        String body = "<div style=\"font-family:Arial,sans-serif;color:#17212b;max-width:600px;\">"
                + "<h1 style=\"color:#0b6e69;\">Vettri HRMS</h1>"
                + "<p>Hi " + escape(toName) + ",</p>"
                + "<p>Your Vettri HRMS employee account has been created.</p>"
                + row("Employee ID", escape(employeeCode))
                + "<p>Set your password to activate your account and access your employee dashboard.</p>"
                + "<p><a href=\"" + escape(activationLink) + "\" style=\"display:inline-block;padding:12px 22px;background:#0b6e69;color:#fff;text-decoration:none;border-radius:5px;font-weight:700;\">Create Password</a></p>"
                + "<p style=\"font-size:13px;color:#52606d;\">If the button does not work, copy and paste this link:</p>"
                + "<p style=\"word-break:break-all;font-size:13px;\"><code>" + escape(activationLink) + "</code></p>"
                + "<p>This invitation expires on " + escape(expiresAt.toString()) + ". Please request a new invitation if it has expired.</p>"
                + "<p>Regards,<br>Vettri HRMS</p></div>";
        return sendAndReport(toEmail, toName, subject, body);
    }

    public void sendAgentTokenOtpEmail(String toEmail, String toName, String otp, int expiryMinutes, String deviceName) {
        String subject = "Vettri HRMS agent token verification code";
        String body = "<p>Hello " + escape(toName) + ",</p>"
                + "<p>Use this one-time code to rotate the agent token for <strong>" + escape(deviceName) + "</strong>:</p>"
                + "<p style=\"font-size:28px;font-weight:700;letter-spacing:6px\">" + escape(otp) + "</p>"
                + "<p>This code expires in " + expiryMinutes + " minutes and can be used once.</p>";
        send(toEmail, toName, subject, body);
    }

    private void send(String toEmail, String toName, String subject, String htmlBody) {
        sendAndReport(toEmail, toName, subject, htmlBody);
    }

    private String applicationUrl() {
        String value = frontendUrl == null ? "" : frontendUrl.trim();
        if (value.isBlank() || value.contains(",") || !(value.startsWith("http://") || value.startsWith("https://"))) {
            throw new IllegalStateException("APP_FRONTEND_URL must contain exactly one HTTP application URL");
        }
        return value.replaceAll("/+$", "");
    }

    private boolean sendAndReport(String toEmail, String toName, String subject, String htmlBody) {
        if (mailPassword == null || mailPassword.isBlank()) {
            log.warn("SMTP email is not configured; email not sent. To: {} <{}>, Subject: {}", toName, toEmail, subject);
            return false;
        }

        try {
            sendHtmlEmail(toEmail, toName, subject, htmlBody, null, null);
            log.info("Sent email to {} <{}>: {}", toName, toEmail, subject);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email to {} <{}>: {}", toName, toEmail, e.getMessage(), e);
            return false;
        }
    }

    private boolean sendWithAttachment(String toEmail, String toName, String subject, String htmlBody,
                                        byte[] attachmentBytes, String attachmentFilename) {
        if (mailPassword == null || mailPassword.isBlank()) {
            log.warn("SMTP email is not configured; email not sent. To: {} <{}>, Subject: {}", toName, toEmail, subject);
            return true;
        }

        try {
            sendHtmlEmail(toEmail, toName, subject, htmlBody, null,
                    new Attachment(attachmentFilename, attachmentBytes));
            log.info("Sent email with attachment to {} <{}>: {}", toName, toEmail, subject);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email with attachment to {} <{}>: {}", toName, toEmail, e.getMessage(), e);
            return false;
        }
    }

    /** Sends a plain-text message using the fixed Vettri sender address. */
    public void sendEmail(String toEmail, String subject, String textBody, String replyTo) throws jakarta.mail.MessagingException {
        sendHtmlEmail(toEmail, null, subject, textBody, replyTo, null);
    }

    /** Sends an HTML message using the fixed Vettri sender address. */
    public void sendHtmlEmail(String toEmail, String toName, String subject, String htmlBody, String replyTo)
            throws jakarta.mail.MessagingException {
        sendHtmlEmail(toEmail, toName, subject, htmlBody, replyTo, null);
    }

    private void sendHtmlEmail(String toEmail, String toName, String subject, String body, String replyTo,
                               Attachment attachment) throws jakarta.mail.MessagingException {
        InternetAddress recipient = new InternetAddress(toEmail);
        recipient.validate();
        var message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null, "UTF-8");
        helper.setFrom(new InternetAddress(fromAddress, fromName));
        helper.setTo(recipient);
        helper.setSubject(subject);
        if (replyTo != null && !replyTo.isBlank()) {
            InternetAddress replyAddress = new InternetAddress(replyTo);
            replyAddress.validate();
            helper.setReplyTo(replyAddress);
        }
        helper.setText(body, attachment == null && !body.trim().startsWith("<") ? false : true);
        if (attachment != null) {
            helper.addAttachment(attachment.filename(), new ByteArrayResource(attachment.bytes()));
        }
        mailSender.send(message);
    }

    private record Attachment(String filename, byte[] bytes) {}

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
