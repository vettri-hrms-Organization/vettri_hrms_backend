package com.haodaone.billing.dto;

import com.haodaone.billing.entity.Invoice;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        String invoiceType,
        String status,
        LocalDate invoiceDate,
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        String plan,
        Integer employeeCount,
        String billingCycle,
        BigDecimal unitRate,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal total,
        String currency,
        String paymentMethod,
        String razorpayOrderId,
        String razorpayPaymentId,
        String emailStatus,
        String companyName,
        String companyLegalName,
        String billingAddress,
        String gstin,
        LocalDate dueDate,
        BigDecimal baseAmount,
        BigDecimal seatAddonAmount,
        BigDecimal taxableValue,
        BigDecimal cgstRate,
        BigDecimal cgstAmount,
        BigDecimal sgstRate,
        BigDecimal sgstAmount,
        BigDecimal igstRate,
        BigDecimal igstAmount,
        String taxNote,
        String sacCode,
        String placeOfSupply,
        String sellerGstin,
        String buyerPoNumber,
        Long originalInvoiceId,
        String originalInvoiceNumber,
        String notes
) {
    public static InvoiceResponse from(Invoice invoice) {
        var company = invoice.getCompany();
        return new InvoiceResponse(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceType().name(), invoice.getStatus().name(),
                invoice.getInvoiceDate(), invoice.getBillingPeriodStart(), invoice.getBillingPeriodEnd(), invoice.getPlan(),
                invoice.getEmployeeCount(), invoice.getBillingCycle(), invoice.getUnitRate(), invoice.getSubtotal(),
                invoice.getDiscount(), invoice.getTax(), invoice.getTotal(), invoice.getCurrency(), invoice.getPaymentMethod(),
                invoice.getRazorpayOrderId(), invoice.getRazorpayPaymentId(), invoice.getEmailStatus(),
                company == null ? null : company.getName(),
                invoice.getBuyerLegalName() != null ? invoice.getBuyerLegalName() : company == null ? null : company.getLegalName(),
                invoice.getBuyerAddress() != null ? invoice.getBuyerAddress() : company == null ? null : company.getBillingAddress(),
                invoice.getBuyerGstin() != null ? invoice.getBuyerGstin() : company == null ? null : company.getGstin(),
                invoice.getDueDate(), invoice.getBaseAmount(), invoice.getSeatAddonAmount(), invoice.getTaxableValue(),
                invoice.getCgstRate(), invoice.getCgstAmount(), invoice.getSgstRate(), invoice.getSgstAmount(),
                invoice.getIgstRate(), invoice.getIgstAmount(), invoice.getTaxNote(), invoice.getSacCode(),
                invoice.getPlaceOfSupply(), invoice.getSellerGstin(), invoice.getBuyerPoNumber(),
                invoice.getOriginalInvoice() == null ? null : invoice.getOriginalInvoice().getId(),
                invoice.getOriginalInvoice() == null ? null : invoice.getOriginalInvoice().getInvoiceNumber(),
                invoice.getNotes());
    }
}
