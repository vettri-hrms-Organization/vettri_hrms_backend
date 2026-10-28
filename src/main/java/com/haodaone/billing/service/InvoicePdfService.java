package com.haodaone.billing.service;

import com.haodaone.billing.entity.Invoice;
import com.haodaone.billing.entity.InvoiceStatus;
import com.haodaone.billing.entity.InvoiceType;
import com.haodaone.billing.gst.AmountInWords;
import com.haodaone.billing.gst.GstCalculator;
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

    private final BillingSettings settings;

    public InvoicePdfService(BillingSettings settings) {
        this.settings = settings;
    }

    /**
     * Renders tax invoices (VTR/2026-27/00001), proforma (PENDING renewal)
     * invoices, credit notes and legacy payment-verification receipts.
     * Legacy invoices (no GST fields) fall back to subtotal / tax / total.
     */
    public byte[] generate(Invoice invoice) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float left = 48;
                float right = page.getMediaBox().getWidth() - 48;
                float y = page.getMediaBox().getHeight() - 54;
                InvoiceType type = invoice.getInvoiceType();
                boolean verificationReceipt = type == InvoiceType.PAYMENT_VERIFICATION_RECEIPT;
                boolean creditNote = type == InvoiceType.CREDIT_NOTE;
                boolean proforma = invoice.getStatus() == InvoiceStatus.PENDING;
                String sellerGstin = value(invoice.getSellerGstin(), settings == null ? null : settings.sellerGstin());

                // Seller
                text(content, BOLD, 18, left, y, settings == null ? "Vettri HRMS" : settings.sellerName());
                float sy = y - 16;
                String sellerAddress = settings == null ? null : settings.sellerAddress();
                for (String line : wrap(value(sellerAddress, "India"), 60)) { text(content, REGULAR, 9, left, sy, line); sy -= 12; }
                text(content, REGULAR, 9, left, sy, "GSTIN: " + value(sellerGstin, "not provided")); sy -= 12;
                if (settings != null && settings.sellerPan() != null) { text(content, REGULAR, 9, left, sy, "PAN: " + settings.sellerPan()); sy -= 12; }

                // Title block
                String title = verificationReceipt ? "PAYMENT VERIFICATION RECEIPT" : creditNote ? "CREDIT NOTE"
                        : proforma ? "PROFORMA INVOICE" : sellerGstin != null && invoice.getTaxableValue() != null ? "TAX INVOICE" : "INVOICE";
                float tx = right - 200;
                text(content, BOLD, verificationReceipt ? 11 : 15, tx, y, title);
                text(content, REGULAR, 9, tx, y - 18, (proforma ? "Ref: " : "No: ") + invoice.getInvoiceNumber());
                text(content, REGULAR, 9, tx, y - 31, "Date: " + date(invoice.getInvoiceDate()));
                float ty = y - 44;
                if (proforma && invoice.getDueDate() != null) { text(content, REGULAR, 9, tx, ty, "Due date: " + date(invoice.getDueDate())); ty -= 13; }
                if (invoice.getPlaceOfSupply() != null || invoice.getPlaceOfSupplyCode() != null) {
                    text(content, REGULAR, 9, tx, ty, "Place of supply: " + value(invoice.getPlaceOfSupply(), "-")
                            + (invoice.getPlaceOfSupplyCode() == null ? "" : " (" + invoice.getPlaceOfSupplyCode() + ")"));
                    ty -= 13;
                }
                if (creditNote && invoice.getOriginalInvoice() != null) {
                    text(content, REGULAR, 9, tx, ty, "Against invoice: " + invoice.getOriginalInvoice().getInvoiceNumber()); ty -= 13;
                }

                y = Math.min(sy, ty) - 14;
                line(content, left, y, right, y);

                // Buyer
                var company = invoice.getCompany();
                String buyerName = value(invoice.getBuyerLegalName(), company == null ? null : value(company.getLegalName(), company.getName()));
                String buyerAddress = value(invoice.getBuyerAddress(), company == null ? null : company.getBillingAddress());
                String buyerGstin = value(invoice.getBuyerGstin(), company == null ? null : company.getGstin());
                text(content, BOLD, 10, left, y - 18, "BILL TO");
                float by = y - 34;
                text(content, REGULAR, 10, left, by, value(buyerName, "-")); by -= 13;
                for (String line : wrap(value(buyerAddress, "Billing address not configured"), 80)) { text(content, REGULAR, 9, left, by, line); by -= 12; }
                text(content, REGULAR, 9, left, by, "GSTIN: " + value(buyerGstin, "not provided (unregistered)")); by -= 12;
                if (invoice.getBuyerPoNumber() != null && !invoice.getBuyerPoNumber().isBlank()) { text(content, REGULAR, 9, left, by, "PO number: " + invoice.getBuyerPoNumber()); by -= 12; }

                // Line items
                y = by - 14;
                fill(content, left, y - 22, right - left, 22, 31, 55, 92);
                content.setNonStrokingColor(1f, 1f, 1f);
                text(content, BOLD, 9, left + 8, y - 15, "DESCRIPTION");
                text(content, BOLD, 9, left + 270, y - 15, "HSN/SAC");
                text(content, BOLD, 9, left + 335, y - 15, "QTY");
                text(content, BOLD, 9, left + 375, y - 15, "RATE");
                text(content, BOLD, 9, right - 80, y - 15, "AMOUNT");
                content.setNonStrokingColor(0f, 0f, 0f);
                y -= 40;
                String sac = value(invoice.getSacCode(), verificationReceipt ? null : settings == null ? null : settings.sac());
                String description = verificationReceipt ? "Payment method verification for Vettri HRMS trial"
                        : creditNote ? "Credit against " + (invoice.getOriginalInvoice() == null ? "original invoice" : invoice.getOriginalInvoice().getInvoiceNumber())
                        : "Vettri HRMS " + value(invoice.getPlan(), "") + " subscription (" + value(invoice.getBillingCycle(), "").toLowerCase(Locale.ROOT) + ")";
                BigDecimal base = invoice.getBaseAmount() != null ? invoice.getBaseAmount()
                        : invoice.getSeatAddonAmount() == null ? invoice.getSubtotal() : invoice.getSubtotal();
                text(content, REGULAR, 9, left + 8, y, description);
                text(content, REGULAR, 9, left + 270, y, value(sac, "-"));
                text(content, REGULAR, 9, left + 335, y, invoice.getEmployeeCount() == null || creditNote ? "1" : String.valueOf(invoice.getEmployeeCount()));
                text(content, REGULAR, 9, left + 375, y, invoice.getUnitRate() == null || creditNote ? "-" : plain(invoice.getUnitRate()));
                text(content, REGULAR, 9, right - 80, y, plain(base));
                if (invoice.getBillingPeriodStart() != null && invoice.getBillingPeriodEnd() != null) {
                    y -= 12;
                    text(content, REGULAR, 8, left + 8, y, "Period: " + date(invoice.getBillingPeriodStart()) + " - " + date(invoice.getBillingPeriodEnd()));
                }
                if (invoice.getSeatAddonAmount() != null && invoice.getSeatAddonAmount().signum() != 0) {
                    y -= 16;
                    text(content, REGULAR, 9, left + 8, y, "Prorated seat / plan add-ons (previous period)");
                    text(content, REGULAR, 9, left + 270, y, value(sac, "-"));
                    text(content, REGULAR, 9, right - 80, y, plain(invoice.getSeatAddonAmount()));
                }

                // Totals
                y -= 26;
                line(content, left, y + 10, right, y + 10);
                float lx = right - 230;
                float vx = right - 80;
                String currency = value(invoice.getCurrency(), "INR");
                BigDecimal taxable = invoice.getTaxableValue() != null ? invoice.getTaxableValue() : invoice.getSubtotal();
                text(content, REGULAR, 10, lx, y, "Taxable value"); text(content, REGULAR, 10, vx, y, plain(taxable)); y -= 16;
                if (positive(invoice.getCgstAmount()) || positive(invoice.getSgstAmount())) {
                    text(content, REGULAR, 10, lx, y, "CGST @ " + plain(invoice.getCgstRate()) + "%"); text(content, REGULAR, 10, vx, y, plain(invoice.getCgstAmount())); y -= 16;
                    text(content, REGULAR, 10, lx, y, "SGST @ " + plain(invoice.getSgstRate()) + "%"); text(content, REGULAR, 10, vx, y, plain(invoice.getSgstAmount())); y -= 16;
                } else if (positive(invoice.getIgstAmount())) {
                    text(content, REGULAR, 10, lx, y, "IGST @ " + plain(invoice.getIgstRate()) + "%"); text(content, REGULAR, 10, vx, y, plain(invoice.getIgstAmount())); y -= 16;
                } else if (invoice.getTaxableValue() == null) {
                    text(content, REGULAR, 10, lx, y, "Tax"); text(content, REGULAR, 10, vx, y, plain(invoice.getTax())); y -= 16;
                }
                line(content, lx - 5, y + 8, right, y + 8);
                y -= 8;
                text(content, BOLD, 12, lx, y, creditNote ? "CREDIT TOTAL" : "TOTAL");
                text(content, BOLD, 12, vx - 30, y, currency + " " + plain(invoice.getTotal()));
                y -= 20;
                text(content, REGULAR, 9, left, y, "Amount in words: " + (creditNote ? "(Credit) " : "") + AmountInWords.rupees(invoice.getTotal()));
                y -= 14;
                String taxNote = invoice.getTaxNote();
                if (taxNote == null && sellerGstin == null && !verificationReceipt) taxNote = GstCalculator.NO_GSTIN_NOTE;
                if (taxNote != null) { text(content, REGULAR, 9, left, y, taxNote); y -= 14; }
                if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
                    for (String line : wrap(invoice.getNotes(), 100)) { text(content, REGULAR, 9, left, y, line); y -= 12; }
                }

                // Payment info
                y -= 14;
                text(content, BOLD, 10, left, y, creditNote ? "CREDIT NOTE DETAILS" : "PAYMENT INFORMATION");
                text(content, REGULAR, 9, left, y - 16, "Status: " + invoice.getStatus().name());
                if (!creditNote) {
                    text(content, REGULAR, 9, left, y - 30, "Payment method: " + value(invoice.getPaymentMethod(), proforma ? "-" : "Razorpay"));
                    text(content, REGULAR, 9, left, y - 44, "Order ID: " + value(invoice.getRazorpayOrderId(), "-"));
                    text(content, REGULAR, 9, left, y - 58, "Payment ID: " + value(invoice.getRazorpayPaymentId(), "-"));
                }
                if (verificationReceipt) {
                    text(content, REGULAR, 9, left, y - 76, "This payment was made to verify your payment method for the Vettri HRMS trial.");
                } else if (proforma) {
                    text(content, REGULAR, 9, left, y - 76, "This is not a tax invoice. A tax invoice is issued when the payment is received.");
                }
                text(content, REGULAR, 8, left, 42, "Thank you for choosing Vettri HRMS. This is a computer-generated document and needs no signature.");
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate invoice PDF", exception);
        }
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() != 0;
    }

    private static String date(java.time.LocalDate value) {
        return value == null ? "-" : DATE_FORMAT.format(value);
    }

    private static String plain(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private static java.util.List<String> wrap(String value, int width) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : value.replace("\r", "").replace("\n", ", ").split("\\s+")) {
            if (current.length() + word.length() + 1 > width && current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) current.append(' ');
            current.append(word);
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines.size() > 4 ? lines.subList(0, 4) : lines;
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
