package com.haodaone.letters.service;

import com.haodaone.common.exception.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LetterRendererTest {

    @Test
    void rendersKnownAndCustomPlaceholders() {
        String body = "Dear {{employee.fullName}}, effective {{ custom.effectiveDate }} at {{company.name}}.";
        String out = LetterRenderer.render(body,
                Map.of("employee.fullName", "Asha Rao", "company.name", "Acme Pvt Ltd"),
                Map.of("effectiveDate", "01 April 2026"));
        assertEquals("Dear Asha Rao, effective 01 April 2026 at Acme Pvt Ltd.", out);
    }

    @Test
    void unknownPlaceholdersAreListedInTheError() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> LetterRenderer.validateTemplate("Hi {{employee.salary}} and {{foo}} and {{employee.code}}"));
        assertTrue(ex.getMessage().contains("employee.salary"));
        assertTrue(ex.getMessage().contains("foo"));
        assertFalse(ex.getMessage().contains("employee.code"));
    }

    @Test
    void missingValuesAreReported() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> LetterRenderer.render("Last day {{employee.lastWorkingDate}} for {{custom.reason}}", new HashMap<>(), Map.of()));
        assertTrue(ex.getMessage().contains("employee.lastWorkingDate"));
        assertTrue(ex.getMessage().contains("custom.reason"));
    }

    @Test
    void valuesAreNotReExpandedAndAreSanitised() {
        String out = LetterRenderer.render("Name: {{employee.fullName}}",
                Map.of("employee.fullName", "{{company.name}} ₹ “x”\u0007"), Map.of());
        assertEquals("Name: {{company.name}} Rs. \"x\"", out);
    }

    @Test
    void customFieldsAreDiscovered() {
        assertEquals(List.of("purpose", "effectiveDate"),
                LetterRenderer.customFieldsIn("{{custom.purpose}} {{today}} {{custom.effectiveDate}} {{custom.purpose}}"));
    }

    @Test
    void everyBuiltInTemplateIsValid() {
        for (DefaultLetterTemplates.Definition d : DefaultLetterTemplates.ALL) {
            assertDoesNotThrow(() -> LetterRenderer.validateTemplate(d.body()), d.name());
        }
    }

    @Test
    void moneyUsesIndianGrouping() {
        assertEquals("INR 6,00,000.00", LetterService.money(new BigDecimal("600000")));
    }

    @Test
    void pdfWrapsLongBodiesOverMultiplePages() throws Exception {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            body.append("Paragraph ").append(i).append(" - this sentence is long enough to need wrapping across the width of an A4 page with margins.\n\n");
        }
        byte[] pdf = new LetterPdfService().render(
                new LetterPdfService.Letterhead("Acme Pvt Ltd", "12 MG Road\nBengaluru 560001", null, "R. Iyer", "HR Manager", "Confidential"),
                "Experience Certificate", body.toString());
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertTrue(doc.getNumberOfPages() > 1, "expected multiple pages");
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Experience Certificate"));
            assertTrue(text.contains("Paragraph 79"));
            assertTrue(text.contains("R. Iyer"));
            assertTrue(text.contains("Page 1 of " + doc.getNumberOfPages()));
        }
    }
}
