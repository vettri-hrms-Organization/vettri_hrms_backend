package com.haodaone.billing.service;

import com.haodaone.billing.entity.Invoice;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class InvoicePdfService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    @Value("${app.billing.seller-name:Vettri HRMS}")
    private String sellerName;
    @Value("${app.billing.seller-address:India}")
    private String sellerAddress;
    @Value("${app.billing.seller-gstin:}")
    private String sellerGstin;

    public byte[] generate(Invoice invoice) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float left = 48;
                float right = page.getMediaBox().getWidth() - 48;
                float y = page.getMediaBox().getHeight() - 54;
                text(content, BOLD, 22, left, y, sellerName);
                text(content, REGULAR, 9, left, y - 17, sellerAddress);
                if (sellerGstin != null && !sellerGstin.isBlank()) text(content, REGULAR, 9, left, y - 30, "GSTIN: " + sellerGstin);
                boolean verificationReceipt = invoice.getInvoiceType() == com.haodaone.billing.entity.InvoiceType.PAYMENT_VERIFICATION_RECEIPT;
                text(content, BOLD, verificationReceipt ? 12 : 18, right - (verificationReceipt ? 188 : 150), y, verificationReceipt ? "PAYMENT VERIFICATION RECEIPT" : "INVOICE");
                text(content, REGULAR, 10, right - 150, y - 20, invoice.getInvoiceNumber());
                text(content, REGULAR, 10, right - 150, y - 35, "Date: " + DATE_FORMAT.format(invoice.getInvoiceDate()));

                y -= 78;
                line(content, left, y, right, y);
                text(content, BOLD, 10, left, y - 22, "BILL TO");
                var company = invoice.getCompany();
                text(content, REGULAR, 10, left, y - 40, value(company == null ? null : company.getLegalName(), company == null ? null : company.getName()));
                text(content, REGULAR, 9, left, y - 55, value(company == null ? null : company.getBillingAddress(), "Billing address not configured"));
                if (company != null && company.getGstin() != null && !company.getGstin().isBlank()) text(content, REGULAR, 9, left, y - 70, "GSTIN: " + company.getGstin());

                y -= 112;
                fill(content, left, y - 27, right - left, 27, 31, 55, 92);
                text(content, BOLD, 9, left + 10, y - 17, "DESCRIPTION");
                text(content, BOLD, 9, right - 145, y - 17, "AMOUNT");
                String description = verificationReceipt ? "Payment method verification for Vettri HRMS trial" : invoice.getPlan() + " subscription";
                text(content, REGULAR, 10, left + 10, y - 52, description);
                if (invoice.getBillingPeriodStart() != null && invoice.getBillingPeriodEnd() != null) {
                    text(content, REGULAR, 9, left + 10, y - 68, "Billing period: " + DATE_FORMAT.format(invoice.getBillingPeriodStart()) + " - " + DATE_FORMAT.format(invoice.getBillingPeriodEnd()));
                }
                text(content, REGULAR, 10, right - 145, y - 52, money(invoice.getCurrency(), invoice.getSubtotal()));

                y -= 115;
                text(content, REGULAR, 10, right - 205, y, "Subtotal");
                text(content, REGULAR, 10, right - 80, y, money(invoice.getCurrency(), invoice.getSubtotal()));
                text(content, REGULAR, 10, right - 205, y - 18, "Discount");
                text(content, REGULAR, 10, right - 80, y - 18, money(invoice.getCurrency(), invoice.getDiscount()));
                text(content, REGULAR, 10, right - 205, y - 36, "Tax");
                text(content, REGULAR, 10, right - 80, y - 36, money(invoice.getCurrency(), invoice.getTax()));
                line(content, right - 210, y - 48, right, y - 48);
                text(content, BOLD, 12, right - 205, y - 70, "TOTAL");
                text(content, BOLD, 12, right - 80, y - 70, money(invoice.getCurrency(), invoice.getTotal()));

                y -= 118;
                text(content, BOLD, 10, left, y, "PAYMENT INFORMATION");
                text(content, REGULAR, 9, left, y - 18, "Status: " + invoice.getStatus().name());
                text(content, REGULAR, 9, left, y - 34, "Payment method: " + value(invoice.getPaymentMethod(), "Razorpay"));
                text(content, REGULAR, 9, left, y - 50, "Order ID: " + value(invoice.getRazorpayOrderId(), "-") );
                text(content, REGULAR, 9, left, y - 66, "Payment ID: " + value(invoice.getRazorpayPaymentId(), "-") );
                if (verificationReceipt) {
                    text(content, REGULAR, 9, left, y - 86, "This payment was made to verify your payment method for the Vettri HRMS trial.");
                }
                text(content, REGULAR, 8, left, 42, "Thank you for choosing Vettri HRMS. This document was generated electronically.");
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate invoice PDF", exception);
        }
    }

    private void text(PDPageContentStream content, PDType1Font font, float size, float x, float y, String value) throws IOException {
        content.beginText(); content.setFont(font, size); content.newLineAtOffset(x, y); content.showText(safe(value)); content.endText();
    }
    private void line(PDPageContentStream content, float x1, float y1, float x2, float y2) throws IOException { content.moveTo(x1, y1); content.lineTo(x2, y2); content.stroke(); }
    private void fill(PDPageContentStream content, float x, float y, float width, float height, int r, int g, int b) throws IOException { content.setNonStrokingColor(r, g, b); content.addRect(x, y, width, height); content.fill(); content.setNonStrokingColor(0, 0, 0); }
    private String money(String currency, BigDecimal amount) { return value(currency, "INR") + " " + NumberFormat.getNumberInstance(Locale.US).format(amount == null ? BigDecimal.ZERO : amount); }
    private String value(String primary, String fallback) { return primary == null || primary.isBlank() ? fallback : primary; }
    private String safe(String value) { return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?"); }
}
