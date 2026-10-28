package com.haodaone.letters.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a letter as an A4 PDF: letterhead (optional logo, company name,
 * address), title, word-wrapped body paragraphs flowing over as many pages
 * as needed, a signatory block and a footer with page numbers on every page.
 * Same PDFBox 3 / Standard 14 font approach as PayslipPdfService.
 */
@Service
public class LetterPdfService {

    private static final Logger log = LoggerFactory.getLogger(LetterPdfService.class);

    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final float MARGIN = 56;
    private static final float BODY_SIZE = 11;
    private static final float LEADING = 15;
    private static final float FOOTER_ZONE = 60;

    /** Letterhead content; any field may be null. */
    public record Letterhead(String companyName, String address, byte[] logo, String signatoryName,
                             String signatoryDesignation, String footer) {}

    public byte[] render(Letterhead letterhead, String title, String body) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Writer writer = new Writer(document, letterhead);
            writer.newPage(true);

            if (title != null && !title.isBlank()) {
                writer.ensureSpace(LEADING * 2);
                writer.centered(BOLD, 14, title);
                writer.y -= LEADING * 1.5f;
            }

            String text = body == null ? "" : body.replace("\r", "");
            for (String paragraph : text.split("\n", -1)) {
                if (paragraph.isBlank()) {
                    writer.y -= LEADING * 0.6f;
                    continue;
                }
                for (String line : wrap(paragraph, REGULAR, BODY_SIZE, writer.lineWidth())) {
                    writer.ensureSpace(LEADING);
                    writer.text(REGULAR, BODY_SIZE, MARGIN, writer.y, line);
                    writer.y -= LEADING;
                }
            }

            // Signatory block
            writer.y -= LEADING;
            writer.ensureSpace(LEADING * 5);
            String company = value(letterhead.companyName(), "");
            writer.text(REGULAR, BODY_SIZE, MARGIN, writer.y, "For " + company);
            writer.y -= LEADING * 3;
            if (letterhead.signatoryName() != null && !letterhead.signatoryName().isBlank()) {
                writer.text(BOLD, BODY_SIZE, MARGIN, writer.y, letterhead.signatoryName());
                writer.y -= LEADING;
            }
            writer.text(REGULAR, BODY_SIZE, MARGIN, writer.y, value(letterhead.signatoryDesignation(), "Authorised Signatory"));

            writer.close();
            writer.drawFooters();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate the letter PDF", e);
        }
    }

    /** Greedy word wrap; words longer than a line are hard-split. */
    static List<String> wrap(String paragraph, PDType1Font font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : paragraph.split(" ", -1)) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (width(font, size, candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
            }
            String rest = word;
            while (width(font, size, rest) > maxWidth && rest.length() > 1) {
                int cut = rest.length() - 1;
                while (cut > 1 && width(font, size, rest.substring(0, cut)) > maxWidth) cut--;
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            current.append(rest);
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private static float width(PDType1Font font, float size, String text) throws IOException {
        return font.getStringWidth(pdfSafe(font, text)) / 1000 * size;
    }

    /** Characters the font can't encode are replaced by '?'. */
    static String pdfSafe(PDType1Font font, String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x20 && c <= 0x7E) {
                sb.append(c);
                continue;
            }
            try {
                font.encode(String.valueOf(c));
                sb.append(c);
            } catch (IllegalArgumentException | IOException e) {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static String value(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    /** Tracks the current page / cursor and repeats the letterhead on each new page. */
    private static final class Writer {
        private final PDDocument document;
        private final Letterhead letterhead;
        private PDPageContentStream content;
        private PDRectangle box;
        private PDImageXObject logo;
        private boolean logoLoaded;
        float y;

        Writer(PDDocument document, Letterhead letterhead) {
            this.document = document;
            this.letterhead = letterhead;
        }

        float lineWidth() {
            return box.getWidth() - MARGIN * 2;
        }

        void newPage(boolean first) throws IOException {
            if (content != null) content.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            box = page.getMediaBox();
            content = new PDPageContentStream(document, page);
            y = box.getHeight() - MARGIN;
            drawHeader(first);
        }

        void ensureSpace(float needed) throws IOException {
            if (y - needed < FOOTER_ZONE) newPage(false);
        }

        private void drawHeader(boolean first) throws IOException {
            float top = y;
            float textX = MARGIN;
            PDImageXObject image = logo();
            if (image != null) {
                float h = 42;
                float w = image.getWidth() * (h / image.getHeight());
                if (w > 140) {
                    w = 140;
                    h = image.getHeight() * (w / image.getWidth());
                }
                content.drawImage(image, MARGIN, top - h + 8, w, h);
                textX = MARGIN + w + 12;
            }
            text(BOLD, first ? 16 : 12, textX, top - 6, value(letterhead.companyName(), ""));
            float lineY = top - 22;
            if (first && letterhead.address() != null) {
                for (String addressLine : letterhead.address().replace("\r", "").split("\n")) {
                    if (addressLine.isBlank()) continue;
                    text(REGULAR, 9, textX, lineY, addressLine.trim());
                    lineY -= 11;
                }
            }
            float bottom = Math.min(lineY, top - 40);
            content.setLineWidth(0.8f);
            content.moveTo(MARGIN, bottom);
            content.lineTo(box.getWidth() - MARGIN, bottom);
            content.stroke();
            y = bottom - 26;
        }

        private PDImageXObject logo() {
            if (logoLoaded) return logo;
            logoLoaded = true;
            byte[] bytes = letterhead.logo();
            if (bytes == null || bytes.length == 0) return null;
            try {
                logo = PDImageXObject.createFromByteArray(document, bytes, "logo");
            } catch (IOException | IllegalArgumentException e) {
                log.warn("Letterhead logo could not be embedded: {}", e.getMessage());
                logo = null;
            }
            return logo;
        }

        void text(PDType1Font font, float size, float x, float yPos, String value) throws IOException {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, yPos);
            content.showText(pdfSafe(font, value));
            content.endText();
        }

        void centered(PDType1Font font, float size, String value) throws IOException {
            float w = width(font, size, value);
            text(font, size, (box.getWidth() - w) / 2, y, value);
        }

        void close() throws IOException {
            if (content != null) content.close();
            content = null;
        }

        void drawFooters() throws IOException {
            int total = document.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                PDPage page = document.getPage(i);
                try (PDPageContentStream footer = new PDPageContentStream(document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    float w = page.getMediaBox().getWidth();
                    footer.setLineWidth(0.5f);
                    footer.moveTo(MARGIN, 44);
                    footer.lineTo(w - MARGIN, 44);
                    footer.stroke();
                    String left = value(letterhead.footer(), "");
                    footer.beginText();
                    footer.setFont(REGULAR, 8);
                    footer.newLineAtOffset(MARGIN, 32);
                    String clipped = left;
                    while (!clipped.isEmpty() && width(REGULAR, 8, clipped) > w - MARGIN * 2 - 70) {
                        clipped = clipped.substring(0, clipped.length() - 1);
                    }
                    footer.showText(pdfSafe(REGULAR, clipped));
                    footer.endText();
                    String pageLabel = "Page " + (i + 1) + " of " + total;
                    float pw = width(REGULAR, 8, pageLabel);
                    footer.beginText();
                    footer.setFont(REGULAR, 8);
                    footer.newLineAtOffset(w - MARGIN - pw, 32);
                    footer.showText(pageLabel);
                    footer.endText();
                }
            }
        }
    }
}
