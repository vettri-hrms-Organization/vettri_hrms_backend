package com.haodaone.integration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** ICS escaping / folding and webhook signatures. */
class IntegrationHelpersTest {

    @Test
    void icsTextIsEscapedAndLongLinesFoldAt75Octets() {
        assertEquals("a\\, b\\; c\\nd", CalendarFeedService.text("a, b; c\nd"));
        StringBuilder sb = new StringBuilder();
        CalendarFeedService.line(sb, "SUMMARY:" + "தமிழ் விடுப்பு ".repeat(10));
        for (String physical : sb.toString().split("\r\n")) {
            assertTrue(physical.getBytes(StandardCharsets.UTF_8).length <= 75, physical);
        }
        assertTrue(sb.toString().contains("\r\n "));
        assertEquals("SUMMARY:" + "தமிழ் விடுப்பு ".repeat(10), sb.toString().replace("\r\n ", "").replace("\r\n", ""));
    }

    @Test
    void webhookSignatureIsHmacSha256Hex() {
        // RFC 4231 test case 2.
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                IntegrationEventService.sign("Jefe", "what do ya want for nothing?"));
    }
}
