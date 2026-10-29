package com.haodaone.recruitment.service;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The signup welcome email carries the product overview deck and sends replies to support. */
class WelcomeEmailOverviewTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private EmailService service(boolean overviewEnabled) {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        EmailService service = new EmailService(mailSender, new EmailTemplateService());
        ReflectionTestUtils.setField(service, "systemFromAddress", "customersupport@vettrihrms.in");
        ReflectionTestUtils.setField(service, "systemFromName", "Vettri HRMS");
        ReflectionTestUtils.setField(service, "supportFromAddress", "customersupport@vettrihrms.in");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://app.vettrihrms.in");
        ReflectionTestUtils.setField(service, "welcomeOverviewEnabled", overviewEnabled);
        return service;
    }

    private MimeMessage sendWelcome(EmailService service) {
        service.sendWelcomeEmail("owner@acme.test", "Asha", "Acme", "STARTER", 25, "MONTHLY", "2026-10-29", "2026-11-12");
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        return sent.getValue();
    }

    @Test
    void welcomeEmailAttachesTheOverviewDeckAndRepliesGoToSupport() throws Exception {
        MimeMessage message = sendWelcome(service(true));

        assertTrue(attachmentNames(message).contains("Vettri-HRMS-Overview.pptx"));
        Address[] replyTo = message.getReplyTo();
        assertEquals("customersupport@vettrihrms.in", ((InternetAddress) replyTo[0]).getAddress());
        assertEquals("customersupport@vettrihrms.in", ((InternetAddress) message.getFrom()[0]).getAddress());
    }

    @Test
    void overviewCanBeSwitchedOff() throws Exception {
        MimeMessage message = sendWelcome(service(false));
        assertFalse(attachmentNames(message).contains("Vettri-HRMS-Overview.pptx"));
    }

    private static List<String> attachmentNames(MimeMessage message) throws Exception {
        List<String> names = new ArrayList<>();
        if (message.getContent() instanceof Multipart multipart) collect(multipart, names);
        return names;
    }

    private static void collect(Multipart multipart, List<String> names) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.getFileName() != null) names.add(part.getFileName());
            if (part.getContent() instanceof Multipart nested) collect(nested, names);
        }
    }
}
