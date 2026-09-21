package com.haodaone.recruitment.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "systemFromAddress", "noreply@vettrihrms.in");
        ReflectionTestUtils.setField(emailService, "systemFromName", "Vettri HRMS");
        ReflectionTestUtils.setField(emailService, "billingFromAddress", "billing@vettrihrms.in");
        ReflectionTestUtils.setField(emailService, "billingFromName", "Vettri Billing");
        ReflectionTestUtils.setField(emailService, "supportFromAddress", "customersupport@vettrihrms.in");
        ReflectionTestUtils.setField(emailService, "supportFromName", "Vettri Customer Support");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://app.vettrihrms.in");
        ReflectionTestUtils.setField(emailService, "mailPassword", "test-password");
    }

    @Test
    void sendsSignupWelcomeWithActualTrialDataAndSystemSender() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        doNothing().when(mailSender).send(message);

        emailService.sendWelcomeEmail("customer@example.com", "Asha Rao", "Acme Technologies",
                "TRIAL", 25, null, "2026-09-20", "2026-10-04");

        verify(mailSender).send(message);
        assertEquals("Welcome to Vettri HRMS — Your Trial Has Started", message.getSubject());
        assertEquals("customer@example.com", message.getAllRecipients()[0].toString());
        assertEquals("Vettri HRMS <noreply@vettrihrms.in>", message.getFrom()[0].toString());
        String content = String.valueOf(message.getContent());
        assertTrue(content.contains("Acme Technologies"));
        assertTrue(content.contains("TRIAL"));
        assertTrue(content.contains("25"));
        assertTrue(content.contains("2026-09-20"));
        assertTrue(content.contains("2026-10-04"));
        assertFalse(content.contains("Billing cycle"));
    }

    @Test
    void sendsResendInvitationWithSystemSenderAndNoRawLinkCopy() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        doNothing().when(mailSender).send(message);

        emailService.sendEmployeeInvitationEmail("employee@example.com", "Asha Rao", "EMP001",
                "raw-token-value", java.time.LocalDateTime.of(2026, 9, 23, 12, 0), true, "Acme Technologies");

        verify(mailSender).send(message);
        assertEquals("Your Vettri HRMS invitation has been resent", message.getSubject());
        assertEquals("Vettri HRMS <noreply@vettrihrms.in>", message.getFrom()[0].toString());
        String content = String.valueOf(message.getContent());
        assertTrue(content.contains("A new invitation has been generated"));
        assertTrue(content.contains("Accept Invitation &amp; Set Password"));
        assertTrue(content.contains("https://app.vettrihrms.in/activate-account?token="));
        assertFalse(content.contains("If the button does not work, copy and paste this link"));
    }
}
