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
import java.util.Locale;

/**
 * Shared transactional email sender for recruitment, onboarding, and
 * agent-token notifications. Credentials and SMTP settings are supplied
 * by Spring Boot from environment variables; the service owns the sender
 * address so callers cannot choose an arbitrary From address.
 */
@Service
public class EmailService {

    public enum EmailChannel {
        SYSTEM,
        BILLING,
        SUPPORT
    }

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");
    private final JavaMailSender mailSender;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.email.from-system-address:noreply@vettrihrms.in}")
    private String systemFromAddress;

    @Value("${app.email.from-system-name:Vettri HRMS}")
    private String systemFromName;

    @Value("${app.email.from-billing-address:billing@vettrihrms.in}")
    private String billingFromAddress;

    @Value("${app.email.from-billing-name:Vettri Billing}")
    private String billingFromName;

    @Value("${app.email.from-support-address:customersupport@vettrihrms.in}")
    private String supportFromAddress;

    @Value("${app.email.from-support-name:Vettri Customer Support}")
    private String supportFromName;

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
        String subject = "Welcome to Vettri HRMS, " + toName;
        String loginUrl = applicationUrl() + "/login";
        String body = "<p>Dear " + escape(toName) + ",</p>"
            + "<p>Welcome to Vettri HRMS.</p>"
            + "<p>Your employee account has been created. You can now sign in to access your HR workspace and the features available to you.</p>"
                + "<table style=\"border-collapse:collapse;margin:16px 0;\">"
                + row("Employee ID", escape(employeeCode))
            + row("Login", "<a href=\"" + escape(loginUrl) + "\">Sign in to Vettri HRMS</a>")
                + "</table>"
            + "<p>Need help? Contact our support team at customersupport@vettrihrms.in.</p>";

        send(toEmail, toName, subject, body);
    }

    public boolean sendEmployeeInvitationEmail(String toEmail, String toName, String employeeCode,
                                               String rawToken, LocalDateTime expiresAt) {
        return sendEmployeeInvitationEmail(toEmail, toName, employeeCode, rawToken, expiresAt, false, null);
        }

        public boolean sendEmployeeInvitationEmail(String toEmail, String toName, String employeeCode,
                               String rawToken, LocalDateTime expiresAt, boolean resend,
                               String organizationName) {
        String safeOrganizationName = organizationName == null || organizationName.isBlank()
            ? "Vettri HRMS" : organizationName;
        String subject = resend ? "Your Vettri HRMS invitation has been resent"
            : "You're invited to join " + safeOrganizationName + " on Vettri HRMS";
        String activationLink = applicationUrl() + "/activate-account?token=" + java.net.URLEncoder.encode(rawToken, java.nio.charset.StandardCharsets.UTF_8);
        String body = "<div style=\"font-family:Arial,sans-serif;color:#17212b;max-width:600px;\">"
                + "<h1 style=\"color:#0b6e69;\">Vettri HRMS</h1>"
                + "<p>Hi " + escape(toName) + ",</p>"
            + "<p>" + (resend ? "A new invitation has been generated for " : "Welcome to ")
            + escape(safeOrganizationName) + " on Vettri HRMS.</p>"
                + row("Employee ID", escape(employeeCode))
            + row("Organization", escape(safeOrganizationName))
            + row("Email", escape(toEmail))
            + "<p>Your employee account has been created and you've been invited to access your HRMS workspace.</p>"
            + "<p><a href=\"" + escape(activationLink) + "\" style=\"display:inline-block;padding:12px 22px;background:#0b6e69;color:#fff;text-decoration:none;border-radius:5px;font-weight:700;\">Accept Invitation &amp; Set Password</a></p>"
            + "<p>This invitation will expire on " + escape(expiresAt.toString()) + ". If you were not expecting this invitation, contact your HR administrator.</p>"
            + "<p>Regards,<br>Vettri HRMS</p><p>Need help? customersupport@vettrihrms.in</p></div>";
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
            sendHtmlEmail(toEmail, toName, subject, htmlBody, EmailChannel.SYSTEM, null, null);
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
                sendHtmlEmail(toEmail, toName, subject, htmlBody, EmailChannel.SYSTEM, null,
                    new Attachment(attachmentFilename, attachmentBytes));
            log.info("Sent email with attachment to {} <{}>: {}", toName, toEmail, subject);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email with attachment to {} <{}>: {}", toName, toEmail, e.getMessage(), e);
            return false;
        }
    }

    /** Sends a plain-text message using the system sender profile. */
    public void sendEmail(String toEmail, String subject, String textBody, String replyTo) throws jakarta.mail.MessagingException {
        sendTextEmail(toEmail, subject, textBody, EmailChannel.SYSTEM, replyTo);
    }

    public void sendTextEmail(String toEmail, String subject, String textBody,
                              EmailChannel channel, String replyTo) throws jakarta.mail.MessagingException {
        sendMessage(toEmail, null, subject, textBody, false, channel, replyTo, null);
    }

    /** Sends an HTML message using a verified Vettri sender profile. */
    public void sendHtmlEmail(String toEmail, String toName, String subject, String htmlBody, String replyTo)
            throws jakarta.mail.MessagingException {
        sendHtmlEmail(toEmail, toName, subject, htmlBody, EmailChannel.SYSTEM, replyTo);
    }

    public void sendHtmlEmail(String toEmail, String toName, String subject, String htmlBody,
                              EmailChannel channel, String replyTo) throws jakarta.mail.MessagingException {
        sendMessage(toEmail, toName, subject, htmlBody, true, channel, replyTo, null);
    }

    public void sendTemplateEmail(String toEmail, String toName, String subject, String htmlBody,
                                  EmailChannel channel, String replyTo) throws jakarta.mail.MessagingException {
        sendHtmlEmail(toEmail, toName, subject, htmlBody, channel, replyTo);
    }

    private void sendHtmlEmail(String toEmail, String toName, String subject, String body,
                               EmailChannel channel, String replyTo, Attachment attachment)
            throws jakarta.mail.MessagingException {
        sendMessage(toEmail, toName, subject, body, true, channel, replyTo, attachment);
    }

    private void sendMessage(String toEmail, String toName, String subject, String body, boolean html,
                             EmailChannel channel, String replyTo, Attachment attachment)
            throws jakarta.mail.MessagingException {
        InternetAddress recipient = new InternetAddress(toEmail);
        recipient.validate();
        var message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null, "UTF-8");
        SenderProfile sender = senderProfile(channel);
        try {
            helper.setFrom(new InternetAddress(sender.address(), sender.name()));
        } catch (java.io.UnsupportedEncodingException exception) {
            throw new jakarta.mail.MessagingException("Configured email sender name is invalid", exception);
        }
        helper.setTo(recipient);
        helper.setSubject(subject);
        if (replyTo != null && !replyTo.isBlank()) {
            InternetAddress replyAddress = new InternetAddress(replyTo);
            replyAddress.validate();
            helper.setReplyTo(replyAddress);
        }
        helper.setText(body, html);
        if (attachment != null) {
            helper.addAttachment(attachment.filename(), new ByteArrayResource(attachment.bytes()));
        }
        mailSender.send(message);
    }

    private SenderProfile senderProfile(EmailChannel channel) {
        SenderProfile sender = switch (channel == null ? EmailChannel.SYSTEM : channel) {
            case SYSTEM -> new SenderProfile(systemFromAddress, systemFromName);
            case BILLING -> new SenderProfile(billingFromAddress, billingFromName);
            case SUPPORT -> new SenderProfile(supportFromAddress, supportFromName);
        };
        if (!VERIFIED_SENDERS.contains(sender.address().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Email sender is not a verified Vettri address");
        }
        return sender;
    }

    private static final java.util.Set<String> VERIFIED_SENDERS = java.util.Set.of(
            "noreply@vettrihrms.in", "billing@vettrihrms.in", "customersupport@vettrihrms.in");

    public void sendWelcomeEmail(String toEmail, String customerName, String organizationName,
                     String plan, Integer employeeCount, String billingCycle,
                     String trialStart, String trialEnd) {
        String body = brandedBody("Welcome to Vettri HRMS", "Hello " + escape(customerName) + ",",
            "Welcome to Vettri HRMS. Your trial for <strong>" + escape(organizationName) + "</strong> has successfully started.",
            rowIfPresent("Plan", plan) + rowIfPresent("Employees", employeeCount)
                + rowIfPresent("Billing cycle", billingCycle)
                + rowIfPresent("Trial starts", trialStart) + rowIfPresent("Trial ends", trialEnd),
            "Access your HRMS at <a href=\"" + escape(applicationUrl()) + "\">Vettri HRMS</a>.<br>"
                + "Website: <a href=\"https://vettrihrms.in\">https://vettrihrms.in</a>");
        sendSafely(toEmail, customerName, "Welcome to Vettri HRMS \u2014 Your Trial Has Started", body,
            EmailChannel.SYSTEM, null);
    }

            public void sendVerificationEmail(String toEmail, String customerName, String verificationToken) {
            String verificationLink = applicationUrl() + "/verify-email?token="
                + java.net.URLEncoder.encode(verificationToken, java.nio.charset.StandardCharsets.UTF_8);
            String body = brandedBody("Verify your Vettri HRMS email address", "Hi " + escape(customerName) + ",",
                "Welcome to Vettri HRMS. Please verify your email address to secure your account.",
                "<tr><td style=\"padding:16px 0;\"><a href=\"" + escape(verificationLink)
                    + "\" style=\"display:inline-block;padding:12px 22px;background:#0b6e69;color:#fff;text-decoration:none;border-radius:5px;font-weight:700;\">Verify Email</a></td></tr>"
                    + row("Expires", "This verification link expires in 24 hours."),
                "If you did not create this account, you can safely ignore this email.<br>Regards,<br>Vettri HRMS<br><a href=\"https://vettrihrms.in\">https://vettrihrms.in</a>");
            sendSafely(toEmail, customerName, "Verify your Vettri HRMS email address", body, EmailChannel.SYSTEM, null);
            }

    public void sendPaymentSuccessEmail(String toEmail, String customerName, String organizationName,
                                        String plan, Integer employeeCount, String billingCycle,
                                        java.math.BigDecimal amount, String paymentDate,
                                        String orderId, String paymentId) {
        String body = brandedBody("Payment successful", "Hello " + escape(customerName) + ",",
                "Your Vettri HRMS payment for <strong>" + escape(organizationName) + "</strong> was successful.",
                row("Plan", escape(plan)) + row("Employees", String.valueOf(employeeCount))
                        + row("Billing cycle", escape(billingCycle)) + row("Amount", escape(amount + " INR"))
                        + row("Payment date", escape(paymentDate)) + row("Order reference", escape(orderId))
                        + row("Payment reference", escape(paymentId)),
                "Manage your account at <a href=\"" + escape(applicationUrl()) + "\">Vettri HRMS</a>.");
        sendSafely(toEmail, customerName, "Payment successful - Vettri HRMS", body, EmailChannel.BILLING, null);
    }

    public void sendPaymentFailureEmail(String toEmail, String customerName, String organizationName,
                                        String orderId) {
        String body = brandedBody("Payment failed", "Hello " + escape(customerName) + ",",
                "We could not complete the Vettri HRMS payment for <strong>" + escape(organizationName) + "</strong>.",
                row("Order reference", escape(orderId)),
                "Please retry from <a href=\"" + escape(applicationUrl()) + "\">Vettri HRMS</a> or contact customersupport@vettrihrms.in.");
        sendSafely(toEmail, customerName, "Payment failed - Vettri HRMS", body, EmailChannel.BILLING, null);
    }

    public void sendSupportRequestEmail(String requesterEmail, String requesterName, String organizationName,
                                        String category, String subject, String description) {
        String body = brandedBody("New Vettri HRMS support request", "Hello Support,",
                "A support request was submitted by <strong>" + escape(requesterName)
                        + "</strong> from <strong>" + escape(organizationName) + "</strong>.",
                row("Category", escape(category)) + row("Subject", escape(subject))
                        + row("Description", escape(description)),
                "Reply to this email to contact the requester.");
        sendSafely("customersupport@vettrihrms.in", requesterName,
                "Vettri Support Request - " + subject, body, EmailChannel.SUPPORT, requesterEmail);
    }

    private void sendSafely(String toEmail, String toName, String subject, String body,
                            EmailChannel channel, String replyTo) {
        try {
            sendHtmlEmail(toEmail, toName, subject, body, channel, replyTo);
        } catch (Exception exception) {
            log.error("Failed to send notification email to {} <{}>: {}", toName, toEmail, exception.getMessage(), exception);
        }
    }

    private String brandedBody(String heading, String greeting, String message, String details, String closing) {
        return "<div style=\"font-family:Arial,sans-serif;color:#17212b;max-width:600px;margin:auto;\">"
                + "<h1 style=\"color:#0b6e69;\">" + escape(heading) + "</h1>"
                + "<p>" + greeting + "</p><p>" + message + "</p>"
                + "<table style=\"border-collapse:collapse;margin:16px 0;\">" + details + "</table>"
                + "<p>" + closing + "</p><p>Support: customersupport@vettrihrms.in</p></div>";
    }

    private record Attachment(String filename, byte[] bytes) {}

    private record SenderProfile(String address, String name) {}

    private String row(String label, String value) {
        return "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">" + escape(label) + "</td>"
                + "<td style=\"padding:4px 0;font-weight:600;\">" + value + "</td></tr>";
    }

    private String rowIfPresent(String label, Object value) {
        if (value == null || value.toString().isBlank()) {
            return "";
        }
        return row(label, escape(value.toString()));
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
